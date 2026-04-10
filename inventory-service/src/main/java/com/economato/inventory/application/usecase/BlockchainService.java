package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.event.BlockchainAuditEvent;
import com.economato.inventory.domain.model.LedgerBlock;
import com.economato.inventory.domain.model.StockLedger;
import com.economato.inventory.domain.model.StockSnapshot;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.LedgerBlockRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockSnapshotRepository;
import com.economato.inventory.infrastructure.config.cache.event.NewLedgerTransactionEvent;
import com.economato.inventory.infrastructure.config.security.BlockchainProperties;
import com.economato.inventory.infrastructure.config.security.LedgerProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@Profile({"!test", "kafka-test"})
public class BlockchainService {

    private static final LocalDateTime GENESIS_TIMESTAMP = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
    private static final String GENESIS_PREVIOUS_HASH = "GENESIS";

    private final LedgerBlockRepository blockRepository;
    private final StockLedgerRepository ledgerRepository;
    private final StockSnapshotRepository snapshotRepository;
    private final BlockMiningService blockMiningService;
    private final MerkleTreeService merkleTreeService;
    private final LedgerProperties ledgerProperties;
    private final BlockchainProperties blockchainProperties;
    private final Optional<AuditEventProducer> auditEventProducer;
    private final ReentrantLock miningLock = new ReentrantLock();
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;
    private final Timer verificationTimer;

    public BlockchainService(
            LedgerBlockRepository blockRepository,
            StockLedgerRepository ledgerRepository,
            StockSnapshotRepository snapshotRepository,
            BlockMiningService blockMiningService,
            MerkleTreeService merkleTreeService,
            LedgerProperties ledgerProperties,
            BlockchainProperties blockchainProperties,
            Optional<AuditEventProducer> auditEventProducer,
            PlatformTransactionManager txManager,
            MeterRegistry meterRegistry) {
        this.blockRepository = blockRepository;
        this.ledgerRepository = ledgerRepository;
        this.snapshotRepository = snapshotRepository;
        this.blockMiningService = blockMiningService;
        this.merkleTreeService = merkleTreeService;
        this.ledgerProperties = ledgerProperties;
        this.blockchainProperties = blockchainProperties;
        this.auditEventProducer = auditEventProducer;

        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);

        this.writeTx = new TransactionTemplate(txManager);
        this.writeTx.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);

        this.verificationTimer = Timer.builder("blockchain.verification.duration")
                .description("Blockchain full verification duration")
                .register(meterRegistry);

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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewLedgerTransaction(NewLedgerTransactionEvent ignored) {
        notifyNewTransaction();
    }

    public void notifyNewTransaction() {
        long pending = ledgerRepository.countByBlockIsNull();
        if (pending >= blockchainProperties.getBlockSize()) {
            mineNextBlock();
        }
    }

    @Scheduled(fixedRateString = "${blockchain.mining-interval-ms:30000}")
    public void scheduledMine() {
        mineNextBlock();
    }

    public void mineNextBlock() {
        if (!miningLock.tryLock()) {
            return;
        }

        try {
            ensureGenesisBlock();
            MiningCandidate candidate = readCandidate();
            if (candidate == null) {
                return;
            }

            BlockMiningService.MiningResult miningResult = blockMiningService.mineBlock(
                    candidate.blockNumber(),
                    candidate.previousBlockHash(),
                    candidate.merkleRoot(),
                    candidate.timestamp());

            persistMinedBlock(candidate, miningResult);
        } finally {
            miningLock.unlock();
        }
    }

    public boolean verifyBlockchainIntegrity() {
        return verificationTimer.record(() -> {
            List<LedgerBlock> blocks = blockRepository.findAllByOrderByBlockNumberAsc();
            if (blocks.isEmpty()) {
                return false;
            }

            LedgerBlock previous = blocks.get(0);
            for (int i = 1; i < blocks.size(); i++) {
                LedgerBlock current = blocks.get(i);
                if (!current.getPreviousBlockHash().equals(previous.getBlockHash())) {
                    return false;
                }
                if (!current.getBlockHash().startsWith("0".repeat(Math.max(0, current.getDifficulty())))) {
                    return false;
                }
                previous = current;
            }
            return true;
        });
    }

    private void ensureGenesisBlock() {
        writeTx.executeWithoutResult(status -> {
            Optional<LedgerBlock> latest = blockRepository.findTopByOrderByBlockNumberDesc();
            if (latest.isPresent()) {
                return;
            }

            String merkleRoot = hmacSha256("GENESIS_BLOCK");
            String blockHash = hmacSha256("0|" + GENESIS_PREVIOUS_HASH + "|" + merkleRoot + "|" + GENESIS_TIMESTAMP + "|0");

            LedgerBlock genesis = LedgerBlock.builder()
                    .blockNumber(0L)
                    .previousBlockHash(GENESIS_PREVIOUS_HASH)
                    .merkleRoot(merkleRoot)
                    .blockHash(blockHash)
                    .nonce(0L)
                    .difficulty(0)
                    .timestamp(GENESIS_TIMESTAMP)
                    .transactionCount(0)
                    .hmacKeyVersion(ledgerProperties.getCurrentHmacVersion())
                    .build();

            blockRepository.save(genesis);
            log.info("Genesis block created: hash={}", blockHash);
        });
    }

    private MiningCandidate readCandidate() {
        return readTx.execute(status -> {
            List<StockLedger> pending = ledgerRepository.findPendingTransactionsOrderByIdAsc(
                    PageRequest.of(0, blockchainProperties.getBlockSize()));
            if (pending.isEmpty()) {
                return null;
            }

            LedgerBlock latest = blockRepository.findTopByOrderByBlockNumberDesc()
                    .orElseThrow(() -> new IllegalStateException("Genesis block should exist before mining"));

            List<Long> txIds = pending.stream().map(StockLedger::getId).toList();
            List<String> txHashes = pending.stream().map(StockLedger::getCurrentHash).toList();
            Set<Integer> productIds = new HashSet<>();
            for (StockLedger tx : pending) {
                productIds.add(tx.getProduct().getId());
            }

            String merkleRoot = merkleTreeService.computeMerkleRoot(txHashes);
            return new MiningCandidate(
                    latest.getBlockNumber() + 1,
                    latest.getBlockHash(),
                    merkleRoot,
                    LocalDateTime.now(),
                    txIds,
                    txHashes,
                    new ArrayList<>(productIds));
        });
    }

    private void persistMinedBlock(MiningCandidate candidate, BlockMiningService.MiningResult miningResult) {
        writeTx.executeWithoutResult(status -> {
            long stillPending = ledgerRepository.countByIdInAndBlockIsNull(candidate.txIds());
            if (stillPending != candidate.txIds().size()) {
                log.warn("Mining candidate became stale before persist. expectedPending={} actualPending={}",
                        candidate.txIds().size(), stillPending);
                return;
            }

            LedgerBlock block = LedgerBlock.builder()
                    .blockNumber(candidate.blockNumber())
                    .previousBlockHash(candidate.previousBlockHash())
                    .merkleRoot(candidate.merkleRoot())
                    .blockHash(miningResult.blockHash())
                    .nonce(miningResult.nonce())
                    .difficulty(miningResult.difficulty())
                    .timestamp(candidate.timestamp())
                    .transactionCount(candidate.txIds().size())
                    .hmacKeyVersion(ledgerProperties.getCurrentHmacVersion())
                    .build();

            LedgerBlock savedBlock = blockRepository.save(block);
            int updated = ledgerRepository.assignBlockToTransactions(savedBlock, candidate.txIds());
            if (updated <= 0) {
                log.warn("No transactions were assigned to mined block {}", savedBlock.getBlockNumber());
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
                            .nonce(savedBlock.getNonce())
                            .difficulty(savedBlock.getDifficulty())
                            .transactionCount(savedBlock.getTransactionCount())
                            .hmacKeyVersion(savedBlock.getHmacKeyVersion())
                            .timestamp(savedBlock.getTimestamp())
                            .transactionHashes(candidate.txHashes())
                            .build()));

            log.info("Mined block #{} hash={} txCount={}",
                    savedBlock.getBlockNumber(), savedBlock.getBlockHash(), savedBlock.getTransactionCount());
        });
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
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256", e);
        }
    }

    private record MiningCandidate(
            Long blockNumber,
            String previousBlockHash,
            String merkleRoot,
            LocalDateTime timestamp,
            List<Long> txIds,
            List<String> txHashes,
            List<Integer> productIds) {
    }
}
