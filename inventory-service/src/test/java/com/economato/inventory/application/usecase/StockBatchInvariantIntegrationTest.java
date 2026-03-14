package com.economato.inventory.application.usecase;

import com.economato.inventory.infrastructure.adapter.in.web.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.DirtiesContext;

import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.domain.model.ProductBatch;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.domain.model.Role;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductBatchRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class StockBatchInvariantIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private StockLedgerService stockLedgerService;

    @Autowired
    private ProductBatchService productBatchService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductBatchRepository productBatchRepository;

    @Autowired
    private StockLedgerRepository stockLedgerRepository;

    private Product testProduct;
    private User testUser;

    @BeforeEach
    void setUp() {
        clearDatabase();

        testUser = new User();
        testUser.setName("admin-user");
        testUser.setUser("admin-login");
        testUser.setPassword("secret");
        testUser.setRole(Role.ADMIN);
        testUser = userRepository.saveAndFlush(testUser);

        testProduct = new Product();
        testProduct.setName("Producto Invariante");
        testProduct.setType("Ingrediente");
        testProduct.setUnit("KG");
        testProduct.setUnitPrice(new BigDecimal("10.50"));
        testProduct.setProductCode("INV-TEST-001");
        testProduct.setCurrentStock(BigDecimal.ZERO);
        testProduct.setMinimumStock(BigDecimal.ZERO);
        testProduct = productRepository.saveAndFlush(testProduct);
    }

    private void verifyInvariant() {
        Product currentProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        List<ProductBatch> activeBatches = productBatchRepository.findActiveByProductIdOrderByExpiration(testProduct.getId());
        
        BigDecimal totalBatchStock = activeBatches.stream()
                .map(ProductBatch::getRemainingQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, currentProduct.getCurrentStock().compareTo(totalBatchStock),
            "El stock actual (" + currentProduct.getCurrentStock() + ") no coincide con la suma de lotes activos (" + totalBatchStock + ")");
    }

    @Test
    @Transactional
    @DisplayName("Debe mantener el invariante en una serie de operaciones complejas")
    void testInvariantMaintainedThroughoutOperations() {
        // 1. ENTRADA inicial
        stockLedgerService.recordStockMovement(
                testProduct.getId(),
                new BigDecimal("50.000"),
                MovementType.ENTRADA,
                "Entrada inicial lote A",
                testUser,
                null,
                LocalDate.now().plusDays(10));
        verifyInvariant();

        // 2. ENTRADA secundaria con expiración más cercana (se consumirá primero por FEFO)
        stockLedgerService.recordStockMovement(
                testProduct.getId(),
                new BigDecimal("30.000"),
                MovementType.ENTRADA,
                "Entrada lote B (caduca antes)",
                testUser,
                null,
                LocalDate.now().plusDays(5));
        verifyInvariant();

        // 3. SALIDA parcial
        stockLedgerService.recordStockMovement(
                testProduct.getId(),
                new BigDecimal("-20.000"),
                MovementType.SALIDA,
                "Salida consumo normal",
                testUser,
                null);
        verifyInvariant();

        // 4. SALIDA que cruza lotes (consume los 10 restantes del B y entra al A)
        stockLedgerService.recordStockMovement(
                testProduct.getId(),
                new BigDecimal("-25.000"),
                MovementType.SALIDA,
                "Salida consumo grande",
                testUser,
                null);
        verifyInvariant();

        // 5. MODIFICACION de ajuste positivo
        stockLedgerService.recordManualAdjustment(
                testProduct.getId(),
                new BigDecimal("15.000"),
                MovementType.MODIFICACION,
                "Ajuste positivo",
                testUser,
                null);
        verifyInvariant();

        // 6. MERMA
        stockLedgerService.recordStockMovement(
                testProduct.getId(),
                new BigDecimal("-5.000"),
                MovementType.MERMA,
                "Merma",
                testUser,
                null);
        verifyInvariant();

        // 7. Agotar stock resultante (debería quedar 0 stock y 0 remaining en lotes o vacíos)
        Product currentProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        stockLedgerService.recordStockMovement(
                testProduct.getId(),
                currentProduct.getCurrentStock().negate(),
                MovementType.SALIDA,
                "Vaciado de stock",
                testUser,
                null);
        verifyInvariant();
    }
}
