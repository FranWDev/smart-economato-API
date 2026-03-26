package com.economato.inventory.application.usecase;

import com.economato.inventory.domain.model.*;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.*;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditEventProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class StockLedgerServiceReversalIntegrationTest {

    @MockitoBean
    private AuditEventProducer auditEventProducer;

    @Autowired
    private StockLedgerService stockLedgerService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductBatchRepository productBatchRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Test
    void shouldDeleteBatchWhenRevertingEntry() {
        // Arrange
        User user = new User();
        user.setName("Test User");
        user.setUser("testuser_reversal_" + System.currentTimeMillis());
        user.setPassword("pass");
        user.setRole(com.economato.inventory.domain.model.Role.ADMIN);
        user = userRepository.save(user);

        Product product = new Product();
        product.setName("Product Test");
        product.setProductCode("PROD-TEST-REV-" + System.currentTimeMillis());
        product.setUnit("KG");
        product.setUnitPrice(BigDecimal.ONE);
        product.setCurrentStock(BigDecimal.ZERO);
        product = productRepository.save(product);

        Supplier supplier = Supplier.builder()
                .name("Supplier Rev Test")
                .build();
        supplier = supplierRepository.saveAndFlush(supplier);
        
        String correlationId = "RECV-123";
        StockLedger tx = stockLedgerService.recordStockMovement(
                product.getId(),
                new BigDecimal("10.000"),
                MovementType.ENTRADA,
                "Recepcion Test",
                user,
                null,
                LocalDate.now().plusDays(30),
                correlationId
        );

        List<ProductBatch> createdBatches = productBatchRepository.findByLedgerTransactionId(tx.getId());
        assertEquals(1, createdBatches.size());
        Long batchId = createdBatches.get(0).getId();

        // Act
        stockLedgerService.revertMovement(correlationId, "Reversion Test");

        // Assert
        assertFalse(productBatchRepository.existsById(batchId), "The batch should have been deleted");
    }

    @Test
    void shouldNotDeleteBatchIfUsedAndThrowException() {
        // Arrange
        User user = new User();
        user.setName("Test User 2");
        user.setUser("testuser2_reversal_" + System.currentTimeMillis());
        user.setPassword("pass");
        user.setRole(com.economato.inventory.domain.model.Role.ADMIN);
        user = userRepository.save(user);

        Product product = new Product();
        product.setName("Product Test 2");
        product.setProductCode("PROD-TEST-REV-2-" + System.currentTimeMillis());
        product.setUnit("KG");
        product.setUnitPrice(BigDecimal.ONE);
        product.setCurrentStock(BigDecimal.ZERO);
        product = productRepository.save(product);

        Supplier supplier = Supplier.builder()
                .name("Supplier Rev Test 2")
                .build();
        supplier = supplierRepository.saveAndFlush(supplier);
        
        String correlationId = "RECV-456";
        StockLedger txEntry = stockLedgerService.recordStockMovement(
                product.getId(),
                new BigDecimal("10.000"),
                MovementType.ENTRADA,
                "Recepcion Test 2",
                user,
                null,
                LocalDate.now().plusDays(30),
                correlationId
        );

        List<ProductBatch> createdBatches = productBatchRepository.findByLedgerTransactionId(txEntry.getId());
        Long batchId = createdBatches.get(0).getId();

        // Use the batch
        stockLedgerService.recordManualAdjustment(
                product.getId(),
                new BigDecimal("-2.000"),
                MovementType.SALIDA,
                "Ajuste Manual Test",
                user,
                batchId,
                null
        );

        // Act & Assert
        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> {
            stockLedgerService.revertMovement(correlationId, "Reversion Fails");
        });

        assertTrue(exception.getMessage().contains("ya ha sido utilizado en: Ajuste Manual Test"));
        assertTrue(productBatchRepository.existsById(batchId), "The batch should still exist");
    }
}
