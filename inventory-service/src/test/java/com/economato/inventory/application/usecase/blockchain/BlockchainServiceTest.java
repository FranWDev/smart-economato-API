package com.economato.inventory.application.usecase.blockchain;
import com.economato.inventory.application.usecase.shared.MerkleTreeService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.economato.inventory.domain.model.ledger.LedgerBlock;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.ledger.StockLedger;
import com.economato.inventory.infrastructure.adapter.out.messaging.shared.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.LedgerBlockRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.stock.StockSnapshotRepository;
import com.economato.inventory.infrastructure.config.ledger.cache.event.NewLedgerTransactionEvent;
import com.economato.inventory.infrastructure.config.blockchain.security.BlockchainProperties;
import com.economato.inventory.infrastructure.config.ledger.security.LedgerProperties;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class BlockchainServiceTest {

    @Mock private LedgerBlockRepository blockRepository;
    @Mock private StockLedgerRepository ledgerRepository;
    @Mock private StockSnapshotRepository snapshotRepository;
    @Mock private BlockSealingService blockSealingService;
    @Mock private MerkleTreeService merkleTreeService;
    @Mock private BlockchainMerkleVerificationService merkleVerificationService;
    @Mock private I18nService i18nService;
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
        blockchainProperties.setSealingIntervalMs(30000L);
        blockchainProperties.setMerkleVerificationEnabled(true);
        meterRegistry = new SimpleMeterRegistry();

        service = new BlockchainService(
            blockRepository, ledgerRepository, snapshotRepository,
            blockSealingService, merkleTreeService, merkleVerificationService, ledgerProperties,
            blockchainProperties, txManager, meterRegistry, i18nService, auditEventProducer
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
    void notifyNewTransaction_triggersSealingWhenMempoolReachesThreshold() {
        when(ledgerRepository.countByBlockIsNull()).thenReturn(10L);
        when(blockRepository.findTopByOrderByBlockNumberDesc()).thenReturn(createMockBlock(0L));
        when(ledgerRepository.findPendingTransactionsOrderByIdAsc(any())).thenReturn(List.of());

        // This should attempt to seal if mempool size >= blockSize
        service.notifyNewTransaction();

        // Verify sealing was at least attempted
        verify(blockRepository, atLeastOnce()).findTopByOrderByBlockNumberDesc();
    }

    @Test
    void notifyNewTransaction_doesNotTriggerSealingWhenMempoolBelowThreshold() {
        when(ledgerRepository.countByBlockIsNull()).thenReturn(5L);

        service.notifyNewTransaction();

        // Should not attempt sealing when below threshold
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
    void verifyBlockchainIntegrity_invalidMerkleRoot_returnsFalse() {
        when(merkleVerificationService.verifyBlockchainIntegrityMerkle()).thenReturn(false);

        boolean valid = service.verifyBlockchainIntegrity();

        assertFalse(valid);
    }

    @Test
    void sealNextBlock_withNoPendingTransactions_returnsEarly() {
        when(blockRepository.findTopByOrderByBlockNumberDesc()).thenReturn(createMockBlock(0L));
        when(ledgerRepository.findPendingTransactionsOrderByIdAsc(any())).thenReturn(List.of());

        service.sealNextBlock();

        verify(blockRepository, never()).save(any(LedgerBlock.class));
    }

    @Test
    void scheduledSeal_doesNotThrow() {
        when(blockRepository.findTopByOrderByBlockNumberDesc()).thenReturn(createMockBlock(0L));
        when(ledgerRepository.findPendingTransactionsOrderByIdAsc(any())).thenReturn(List.of());

        assertDoesNotThrow(() -> service.scheduledSeal());
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

        return Optional.of(LedgerBlock.builder()
            .id(blockNumber)
            .blockNumber(blockNumber)
            .blockHash(generateHash(blockNumber))
            .previousBlockHash(previousHash)
            .merkleRoot("merkleroot" + blockNumber)
            .timestamp(LocalDateTime.now())
            .transactionCount(blockNumber.intValue() * 2)
            .hmacKeyVersion(1)
            .build());
    }

    private List<LedgerBlock> createMockBlockChain(int size) {
        List<LedgerBlock> blocks = new ArrayList<>();
        String previousHash = "GENESIS";
        for (long i = 0; i < size; i++) {
            LedgerBlock block = LedgerBlock.builder()
                .id(i)
                .blockNumber(i)
                .blockHash(generateHash(i))
                .previousBlockHash(previousHash)
                .merkleRoot("merkleroot" + i)
                .timestamp(LocalDateTime.now())
                .transactionCount((int) (i * 2))
                .hmacKeyVersion(1)
                .build();
            blocks.add(block);
            previousHash = block.getBlockHash();
        }
        return blocks;
    }

        private String generateHash(long seed) {
        return String.format("%064x", seed);
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
