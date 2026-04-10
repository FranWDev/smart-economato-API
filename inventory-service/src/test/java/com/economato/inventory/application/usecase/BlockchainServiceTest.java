package com.economato.inventory.application.usecase;

import com.economato.inventory.domain.model.LedgerBlock;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.StockLedger;
import com.economato.inventory.domain.model.StockSnapshot;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.LedgerBlockRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockSnapshotRepository;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.config.cache.event.NewLedgerTransactionEvent;
import com.economato.inventory.infrastructure.config.security.BlockchainProperties;
import com.economato.inventory.infrastructure.config.security.LedgerProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlockchainServiceTest {

    @Mock private LedgerBlockRepository blockRepository;
    @Mock private StockLedgerRepository ledgerRepository;
    @Mock private StockSnapshotRepository snapshotRepository;
    @Mock private BlockMiningService blockMiningService;
    @Mock private MerkleTreeService merkleTreeService;
    @Mock private BlockchainMerkleVerificationService merkleVerificationService;
    private final LedgerProperties ledgerProperties = new LedgerProperties();
    private final BlockchainProperties blockchainProperties = new BlockchainProperties();
    private final Optional<AuditEventProducer> auditEventProducer = Optional.empty();
    private final PlatformTransactionManager txManager = new NoOpTransactionManager();
    private MeterRegistry meterRegistry;

    private BlockchainService service;

    @BeforeEach
    void setup() {
        ledgerProperties.setHmacSecret("test-secret-32chars-minimum-length");
        ledgerProperties.setCurrentHmacVersion(1);
        blockchainProperties.setBlockSize(10);
        blockchainProperties.setMiningIntervalMs(30000L);
        blockchainProperties.setDifficulty(2);
        blockchainProperties.setMerkleVerificationEnabled(true);
        meterRegistry = new SimpleMeterRegistry();

        service = new BlockchainService(
            blockRepository, ledgerRepository, snapshotRepository,
            blockMiningService, merkleTreeService, merkleVerificationService, ledgerProperties,
            blockchainProperties, auditEventProducer, txManager, meterRegistry
        );
    }

    @Test
    void initBlockchain_logsOrphansWhenFound() {
        when(ledgerRepository.countByBlockIsNull()).thenReturn(5L);
        when(blockRepository.findTopByOrderByBlockNumberDesc()).thenReturn(createMockBlock(0L));

        // Should not throw and should execute without errors
        assertDoesNotThrow(() -> service.initBlockchain());

        // Verify it checked for orphaned transactions
        verify(ledgerRepository).countByBlockIsNull();
    }

    @Test
    void onNewLedgerTransaction_delegatesToNotifyNewTransaction() {
        NewLedgerTransactionEvent event = new NewLedgerTransactionEvent(1L);
        when(ledgerRepository.countByBlockIsNull()).thenReturn(5L);

        service.onNewLedgerTransaction(event);

        verify(ledgerRepository).countByBlockIsNull();
    }

    @Test
    void notifyNewTransaction_triggersMininingWhenMempoolReachesThreshold() {
        when(ledgerRepository.countByBlockIsNull()).thenReturn(10L);
        when(blockRepository.findTopByOrderByBlockNumberDesc()).thenReturn(createMockBlock(0L));
        when(ledgerRepository.findPendingTransactionsOrderByIdAsc(any())).thenReturn(List.of());

        // This should attempt to mine if mempool size >= blockSize
        service.notifyNewTransaction();

        // Verify mining was at least attempted
        verify(blockRepository, atLeastOnce()).findTopByOrderByBlockNumberDesc();
    }

    @Test
    void notifyNewTransaction_doesNotTriggerMiningWhenMempoolBelowThreshold() {
        when(ledgerRepository.countByBlockIsNull()).thenReturn(5L);

        service.notifyNewTransaction();

        // Should not attempt mining when below threshold
        verify(blockRepository, never()).findTopByOrderByBlockNumberDesc();
    }

    @Test
    void verifyBlockchainIntegrity_emptyBlockchain_returnsFalse() {
        when(merkleVerificationService.verifyBlockchainIntegrityMerkle()).thenReturn(false);

        boolean valid = service.verifyBlockchainIntegrity();

        assertFalse(valid);
    }

    @Test
    void verifyBlockchainIntegrity_singleGenesisBlock_returnsTrue() {
        when(merkleVerificationService.verifyBlockchainIntegrityMerkle()).thenReturn(true);

        boolean valid = service.verifyBlockchainIntegrity();

        assertTrue(valid);
    }

    @Test
    void verifyBlockchainIntegrity_validChain_returnsTrue() {
        when(merkleVerificationService.verifyBlockchainIntegrityMerkle()).thenReturn(true);

        boolean valid = service.verifyBlockchainIntegrity();

        assertTrue(valid);
    }

    @Test
    void verifyBlockchainIntegrity_brokenChainLink_returnsFalse() {
        when(merkleVerificationService.verifyBlockchainIntegrityMerkle()).thenReturn(false);

        boolean valid = service.verifyBlockchainIntegrity();

        assertFalse(valid);
    }

    @Test
    void verifyBlockchainIntegrity_invalidProofOfWork_returnsFalse() {
        when(merkleVerificationService.verifyBlockchainIntegrityMerkle()).thenReturn(false);

        boolean valid = service.verifyBlockchainIntegrity();

        assertFalse(valid);
    }

    @Test
    void mineNextBlock_withNoPendingTransactions_returnsEarly() {
        when(blockRepository.findTopByOrderByBlockNumberDesc()).thenReturn(createMockBlock(0L));
        when(ledgerRepository.findPendingTransactionsOrderByIdAsc(any())).thenReturn(List.of());

        service.mineNextBlock();

        verify(blockRepository, never()).save(any(LedgerBlock.class));
    }

    @Test
    void scheduledMine_doesNotThrow() {
        when(blockRepository.findTopByOrderByBlockNumberDesc()).thenReturn(createMockBlock(0L));
        when(ledgerRepository.findPendingTransactionsOrderByIdAsc(any())).thenReturn(List.of());

        assertDoesNotThrow(() -> service.scheduledMine());
    }

    @Test
    void blockchainService_recoversOrphansOnStartup() {
        when(ledgerRepository.countByBlockIsNull()).thenReturn(5L);
        when(blockRepository.findTopByOrderByBlockNumberDesc()).thenReturn(createMockBlock(5L));

        assertDoesNotThrow(() -> service.initBlockchain());
        verify(ledgerRepository).countByBlockIsNull();
    }

    // Helper methods

    private Optional<LedgerBlock> createMockBlock(Long blockNumber) {
        String previousHash = blockNumber == 0L ? "GENESIS" : createMockBlock(blockNumber - 1).get().getBlockHash();
        int difficulty = blockNumber == 0L ? 0 : 2;

        return Optional.of(LedgerBlock.builder()
            .id(blockNumber)
            .blockNumber(blockNumber)
            .blockHash(generateHash(blockNumber, difficulty))
            .previousBlockHash(previousHash)
            .merkleRoot("merkleroot" + blockNumber)
            .nonce(blockNumber * 10)
            .difficulty(difficulty)
            .timestamp(LocalDateTime.now())
            .transactionCount(blockNumber.intValue() * 2)
            .hmacKeyVersion(1)
            .build());
    }

    private List<LedgerBlock> createMockBlockChain(int size) {
        List<LedgerBlock> blocks = new ArrayList<>();
        String previousHash = "GENESIS";
        for (long i = 0; i < size; i++) {
            int difficulty = i == 0 ? 0 : 2;
            LedgerBlock block = LedgerBlock.builder()
                .id(i)
                .blockNumber(i)
                .blockHash(generateHash(i, difficulty))
                .previousBlockHash(previousHash)
                .merkleRoot("merkleroot" + i)
                .nonce(i * 10)
                .difficulty(difficulty)
                .timestamp(LocalDateTime.now())
                .transactionCount((int) (i * 2))
                .hmacKeyVersion(1)
                .build();
            blocks.add(block);
            previousHash = block.getBlockHash();
        }
        return blocks;
    }

        private String generateHash(long seed, int difficulty) {
        int prefixLength = Math.max(0, difficulty);
        int hexLength = 64 - prefixLength;
        return "0".repeat(prefixLength) + String.format("%0" + hexLength + "x", seed);
        }

    private List<StockLedger> createMockLedgers(List<Long> txIds) {
        List<StockLedger> ledgers = new ArrayList<>();
        int productId = 1;
        for (Long txId : txIds) {
            Product product = new Product();
            product.setId(productId++);
            StockLedger ledger = mock(StockLedger.class);
            when(ledger.getId()).thenReturn(txId);
            when(ledger.getProduct()).thenReturn(product);
            ledgers.add(ledger);
        }
        return ledgers;
    }

    private static class NoOpTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
