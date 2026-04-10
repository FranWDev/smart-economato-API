package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.response.IntegrityCheckResult;
import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.ProductBatch;
import com.economato.inventory.domain.model.StockLedger;
import com.economato.inventory.domain.model.StockSnapshot;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductBatchRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockSnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "blockchain.ledger-merkle-verification-enabled=true")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class StockLedgerServiceMerkleIntegrationTest {

    @MockitoBean
    private AuditEventProducer auditEventProducer;

    @Autowired
    private StockLedgerService stockLedgerService;

    @Autowired
    private StockLedgerRepository ledgerRepository;

    @Autowired
    private StockSnapshotRepository snapshotRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductBatchRepository productBatchRepository;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        ledgerRepository.deleteAll();
        snapshotRepository.deleteAll();
        productBatchRepository.deleteAll();

        testProduct = new Product();
        testProduct.setName("Merkle Product");
        testProduct.setUnit("KG");
        testProduct.setUnitPrice(new BigDecimal("10.00"));
        testProduct.setProductCode("MERKLE-TEST-001");
        testProduct.setCurrentStock(new BigDecimal("100.0"));
        testProduct = productRepository.saveAndFlush(testProduct);

        ProductBatch batch = ProductBatch.builder()
                .product(testProduct)
                .initialQuantity(testProduct.getCurrentStock())
                .remainingQuantity(testProduct.getCurrentStock())
                .expirationDate(LocalDate.now().plusYears(1))
                .receivedAt(java.time.LocalDateTime.now())
                .depleted(false)
                .build();
        productBatchRepository.saveAndFlush(batch);
    }

    @Test
    void verifyChainIntegrity_merkleEnabled_returnsValidResultForCleanChain() {
        stockLedgerService.recordStockMovement(
                testProduct.getId(),
                new BigDecimal("25.0"),
                MovementType.ENTRADA,
                "Merkle TX1",
                null,
                null,
                LocalDate.now().plusDays(30));

        stockLedgerService.recordStockMovement(
                testProduct.getId(),
                new BigDecimal("-10.0"),
                MovementType.SALIDA,
                "Merkle TX2",
                null,
                null,
                LocalDate.now().plusDays(30));

        IntegrityCheckResult result = stockLedgerService.verifyChainIntegrity(testProduct.getId());

        assertTrue(result.isValid());
        assertNotNull(result.getMessage());
        assertNull(result.getErrors());

        Optional<StockSnapshot> snapshot = snapshotRepository.findById(testProduct.getId());
        assertTrue(snapshot.isPresent());
        assertEquals("VALID", snapshot.get().getIntegrityStatus());
    }
}
