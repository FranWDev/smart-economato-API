package com.economato.inventory.application.usecase.blockchain;
import com.economato.inventory.application.usecase.shared.MerkleTreeService;
import com.economato.inventory.application.dto.blockchain.response.BlockchainStatsResponseDTO;
import com.economato.inventory.application.dto.blockchain.response.BlockchainVerificationResponseDTO;
import com.economato.inventory.application.dto.ledger.response.LedgerBlockResponseDTO;
import com.economato.inventory.application.dto.ledger.response.StockLedgerResponseDTO;
import com.economato.inventory.application.mapper.ledger.StockLedgerMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.economato.inventory.application.dto.blockchain.event.BlockchainAuditEvent;
import com.economato.inventory.domain.model.ledger.LedgerBlock;
import com.economato.inventory.domain.model.ledger.StockLedger;
import com.economato.inventory.domain.model.stock.StockSnapshot;
import com.economato.inventory.infrastructure.adapter.out.messaging.shared.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.LedgerBlockRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.stock.StockSnapshotRepository;
import com.economato.inventory.infrastructure.config.ledger.cache.event.NewLedgerTransactionEvent;
import com.economato.inventory.infrastructure.config.blockchain.security.BlockchainProperties;
import com.economato.inventory.infrastructure.config.ledger.security.LedgerProperties;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Profile({ "!test", "kafka-test" })
public class BlockchainService {

    private static final LocalDateTime GENESIS_TIMESTAMP = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
    private static final String GENESIS_PREVIOUS_HASH = "GENESIS";

    private final LedgerBlockRepository blockRepository;
    private final StockLedgerRepository ledgerRepository;
    private final StockSnapshotRepository snapshotRepository;
    private final BlockSealingService blockSealingService;
    private final MerkleTreeService merkleTreeService;
    private final BlockchainMerkleVerificationService merkleVerificationService;
    private final LedgerProperties ledgerProperties;
    private final BlockchainProperties blockchainProperties;
    private final Optional<AuditEventProducer> auditEventProducer;
    private final ReentrantLock sealingLock = new ReentrantLock();
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;
    private final I18nService i18nService;
    private final StockLedgerMapper stockLedgerMapper;

    public BlockchainService(
            LedgerBlockRepository blockRepository,
            StockLedgerRepository ledgerRepository,
            StockSnapshotRepository snapshotRepository,
            BlockSealingService blockSealingService,
            MerkleTreeService merkleTreeService,
            BlockchainMerkleVerificationService merkleVerificationService,
            LedgerProperties ledgerProperties,
            BlockchainProperties blockchainProperties,
            PlatformTransactionManager txManager,
            MeterRegistry meterRegistry,
            I18nService i18nService,
            StockLedgerMapper stockLedgerMapper,
            Optional<AuditEventProducer> auditEventProducer) {
        this.i18nService = i18nService;
        this.blockRepository = blockRepository;
        this.ledgerRepository = ledgerRepository;
        this.snapshotRepository = snapshotRepository;
        this.blockSealingService = blockSealingService;
        this.merkleTreeService = merkleTreeService;
        this.merkleVerificationService = merkleVerificationService;
        this.ledgerProperties = ledgerProperties;
        this.blockchainProperties = blockchainProperties;
        this.stockLedgerMapper = stockLedgerMapper;
        this.auditEventProducer = auditEventProducer;

        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
        this.readTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        this.writeTx = new TransactionTemplate(txManager);
        this.writeTx.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        this.writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Gauge.builder("blockchain.mempool.size", ledgerRepository, repo -> (double) repo.countByBlockIsNull())
                .description("Current persisted mempool size")
                .register(meterRegistry);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initBlockchain() {
        ensureGenesisBlock();
        long orphaned = ledgerRepository.countByBlockIsNull();
        if (orphaned > 0) {
            log.warn("Recovered {} orphaned persisted mempool transactions at startup", orphaned);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewLedgerTransaction(NewLedgerTransactionEvent ignored) {
        notifyNewTransaction();
    }

    public void notifyNewTransaction() {
        long pending = ledgerRepository.countByBlockIsNull();
        if (pending >= blockchainProperties.getBlockSize()) {
            sealNextBlock();
        }
    }

    @Scheduled(fixedRateString = "${blockchain.sealing-interval-ms:30000}")
    public void scheduledSeal() {
        sealNextBlock();
    }

    public void sealNextBlock() {
        if (!sealingLock.tryLock()) {
            return;
        }

        try {
            ensureGenesisBlock();
            SealingCandidate candidate = readCandidate();
            if (candidate == null) {
                return;
            }

            BlockSealingService.SealingResult sealingResult = blockSealingService.sealBlock(
                    candidate.blockNumber(),
                    candidate.previousBlockHash(),
                    candidate.merkleRoot(),
                    candidate.timestamp());

            persistSealedBlock(candidate, sealingResult);
        } finally {
            sealingLock.unlock();
        }
    }

    public boolean verifyBlockchainIntegrity() {
        if (blockchainProperties.getMerkleVerificationEnabled()) {
            try {
                return merkleVerificationService.verifyBlockchainIntegrityMerkle();
            } catch (Exception e) {
                log.error("Merkle verification failed with exception", e);
                return false;
            }
        }
        return false;
    }

    private void ensureGenesisBlock() {
        try {
            writeTx.executeWithoutResult(status -> {
                Optional<LedgerBlock> latest = blockRepository.findTopByOrderByBlockNumberDesc();
                if (latest.isPresent()) {
                    return;
                }

                String merkleRoot = hmacSha256("GENESIS_BLOCK");
                String blockHash = hmacSha256(
                    "0|" + GENESIS_PREVIOUS_HASH + "|" + merkleRoot + "|" + GENESIS_TIMESTAMP);

                LedgerBlock genesis = LedgerBlock.builder()
                        .blockNumber(0L)
                        .previousBlockHash(GENESIS_PREVIOUS_HASH)
                        .merkleRoot(merkleRoot)
                        .blockHash(blockHash)
                        .timestamp(GENESIS_TIMESTAMP)
                        .transactionCount(0)
                        .hmacKeyVersion(ledgerProperties.getCurrentHmacVersion())
                        .build();

                blockRepository.save(genesis);
                log.info("Genesis block created: hash={}", blockHash);
            });
        } catch (DataIntegrityViolationException e) {
            log.debug("Genesis block collision detected. It was likely created by another thread.");
        }
    }

    private SealingCandidate readCandidate() {
        return readTx.execute(status -> {
            List<StockLedger> pending = ledgerRepository.findPendingTransactionsOrderByIdAsc(
                    PageRequest.of(0, blockchainProperties.getBlockSize()));
            if (pending.isEmpty()) {
                return null;
            }

            LedgerBlock latest = blockRepository.findTopByOrderByBlockNumberDesc()
                    .orElseThrow(() -> new IllegalStateException(
                            i18nService.getMessage(MessageKey.ERROR_INTERNAL_SERVER_ERROR)));

            List<Long> txIds = pending.stream().map(StockLedger::getId).toList();
            List<String> txHashes = pending.stream().map(StockLedger::getCurrentHash).toList();
            Set<Integer> productIds = new HashSet<>();
            for (StockLedger tx : pending) {
                productIds.add(tx.getProduct().getId());
            }

            String merkleRoot = merkleTreeService.computeMerkleRoot(txHashes);
            return new SealingCandidate(
                    latest.getBlockNumber() + 1,
                    latest.getBlockHash(),
                    merkleRoot,
                    LocalDateTime.now(),
                    txIds,
                    txHashes,
                    new ArrayList<>(productIds));
        });
    }

    private void persistSealedBlock(SealingCandidate candidate, BlockSealingService.SealingResult sealingResult) {
        writeTx.executeWithoutResult(status -> {
            long stillPending = ledgerRepository.countByIdInAndBlockIsNull(candidate.txIds());
            if (stillPending != candidate.txIds().size()) {
                log.warn("Sealing candidate became stale before persist. expectedPending={} actualPending={}",
                        candidate.txIds().size(), stillPending);
                return;
            }

            LedgerBlock block = LedgerBlock.builder()
                    .blockNumber(candidate.blockNumber())
                    .previousBlockHash(candidate.previousBlockHash())
                    .merkleRoot(candidate.merkleRoot())
                    .blockHash(sealingResult.blockHash())
                    .timestamp(candidate.timestamp())
                    .transactionCount(candidate.txIds().size())
                    .hmacKeyVersion(ledgerProperties.getCurrentHmacVersion())
                    .build();

            LedgerBlock savedBlock = blockRepository.save(block);
            int updated = ledgerRepository.assignBlockToTransactions(savedBlock, candidate.txIds());
            if (updated <= 0) {
                log.warn("No transactions were assigned to sealed block {}", savedBlock.getBlockNumber());
                return;
            }

            List<StockSnapshot> snapshots = snapshotRepository.findAllById(candidate.productIds());
            for (StockSnapshot snapshot : snapshots) {
                snapshot.setLastBlockNumber(savedBlock.getBlockNumber());
                snapshot.setLastBlockHash(savedBlock.getBlockHash());
            }
            snapshotRepository.saveAll(snapshots);

            auditEventProducer.ifPresent(producer -> producer.publishBlockchainEvent(
                    BlockchainAuditEvent.builder()
                            .blockNumber(savedBlock.getBlockNumber())
                            .blockHash(savedBlock.getBlockHash())
                            .previousBlockHash(savedBlock.getPreviousBlockHash())
                            .merkleRoot(savedBlock.getMerkleRoot())
                            .transactionCount(savedBlock.getTransactionCount())
                            .hmacKeyVersion(savedBlock.getHmacKeyVersion())
                            .timestamp(savedBlock.getTimestamp())
                            .transactionHashes(candidate.txHashes())
                            .build()));

            log.info("Sealed block #{} hash={} txCount={}",
                    savedBlock.getBlockNumber(), savedBlock.getBlockHash(), savedBlock.getTransactionCount());
        });
    }

    @Transactional(readOnly = true)
    public BlockchainVerificationResponseDTO verifyBlockchain() {
        boolean valid = verifyBlockchainIntegrity();
        long blockCount = blockRepository.count();
        long pendingTransactions = ledgerRepository.countByBlockIsNull();
        LedgerBlock latestBlock = blockRepository.findTopByOrderByBlockNumberDesc().orElse(null);

        return BlockchainVerificationResponseDTO.builder()
                .valid(valid)
                .message(valid ? "Blockchain íntegra" : "Blockchain con inconsistencias")
                .blockCount(blockCount)
                .pendingTransactions(pendingTransactions)
                .latestBlockNumber(latestBlock != null ? latestBlock.getBlockNumber() : null)
                .latestBlockHash(latestBlock != null ? latestBlock.getBlockHash() : null)
                .build();
    }

    @Transactional(readOnly = true)
    public BlockchainStatsResponseDTO getStats() {
        LedgerBlock latestBlock = blockRepository.findTopByOrderByBlockNumberDesc().orElse(null);
        boolean valid = verifyBlockchainIntegrity();

        return BlockchainStatsResponseDTO.builder()
                .blockCount(blockRepository.count())
                .pendingTransactions(ledgerRepository.countByBlockIsNull())
                .latestBlockNumber(latestBlock != null ? latestBlock.getBlockNumber() : null)
                .latestBlockHash(latestBlock != null ? latestBlock.getBlockHash() : null)
                .valid(valid)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<LedgerBlockResponseDTO> getBlocks(Pageable pageable) {
        return blockRepository.findAll(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<LedgerBlockResponseDTO> getBlock(Long blockNumber) {
        return blockRepository.findByBlockNumber(blockNumber)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<StockLedgerResponseDTO> getMempool(Pageable pageable) {
        List<StockLedger> pending = ledgerRepository.findPendingTransactionsOrderByIdAsc(pageable);
        long total = ledgerRepository.countByBlockIsNull();
        return new PageImpl<>(pending.stream()
                .map(stockLedgerMapper::toDTO)
                .toList(), pageable, total);
    }

    private LedgerBlockResponseDTO toDto(LedgerBlock block) {
        return LedgerBlockResponseDTO.builder()
                .blockNumber(block.getBlockNumber())
                .previousBlockHash(block.getPreviousBlockHash())
                .merkleRoot(block.getMerkleRoot())
                .blockHash(block.getBlockHash())
                .timestamp(block.getTimestamp())
                .transactionCount(block.getTransactionCount())
                .hmacKeyVersion(block.getHmacKeyVersion())
                .build();
    }

    private String hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            String secret = ledgerProperties.getHmacSecretForVersion(ledgerProperties.getCurrentHmacVersion());
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hashBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(i18nService.getMessage(MessageKey.ERROR_BLOCK_HASH_CALCULATION_FAILED), e);
        }
    }

    private record SealingCandidate(
            Long blockNumber,
            String previousBlockHash,
            String merkleRoot,
            LocalDateTime timestamp,
            List<Long> txIds,
            List<String> txHashes,
            List<Integer> productIds) {
    }
}
