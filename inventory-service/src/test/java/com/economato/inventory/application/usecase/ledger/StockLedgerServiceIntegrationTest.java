package com.economato.inventory.application.usecase.ledger;
import com.economato.inventory.application.usecase.product.ProductBatchService;
import com.economato.inventory.application.usecase.weeklyplan.WeeklyPlanStockReservationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.infrastructure.adapter.in.web.shared.InvalidOperationException;
import com.economato.inventory.application.dto.shared.response.IntegrityCheckResult;
import com.economato.inventory.domain.model.shared.MovementType;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.product.ProductBatch;
import com.economato.inventory.domain.model.ledger.StockLedger;
import com.economato.inventory.domain.model.stock.StockSnapshot;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductBatchRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.stock.StockSnapshotRepository;
import com.economato.inventory.infrastructure.adapter.out.messaging.shared.kafka.producer.AuditEventProducer;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "blockchain.ledger-merkle-verification-enabled=false")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class StockLedgerServiceIntegrationTest {

        @MockitoBean
        private AuditEventProducer auditEventProducer;

        @MockitoBean
        private WeeklyPlanStockReservationService weeklyPlanStockReservationService;

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

        @Autowired
        private ProductBatchService productBatchService;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Autowired
        private EntityManager entityManager;

        private Product testProduct;
        private User testUser;

        @BeforeEach
        void setUp() {

                ledgerRepository.deleteAll();
                snapshotRepository.deleteAll();
                productBatchRepository.deleteAll();

                testProduct = new Product();
                testProduct.setName("Test Product - Ledger");
                testProduct.setUnit("KG");
                testProduct.setUnitPrice(new BigDecimal("10.50"));
                testProduct.setProductCode("LEDGER-TEST-001");
                testProduct.setCurrentStock(new BigDecimal("100.0"));
 // Required field
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

                testUser = null;

                when(weeklyPlanStockReservationService.getReservedStockForProduct(anyInt())).thenReturn(BigDecimal.ZERO);
                when(weeklyPlanStockReservationService.calculateReservedStock(any())).thenReturn(java.util.Collections.emptyMap());
        }

        @Test
        @Transactional
        @DisplayName(" Debe registrar una transacción correctamente")
        void testRecordStockMovement_Success() {

                BigDecimal delta = new BigDecimal("50.0");
                MovementType movementType = MovementType.ENTRADA;
                String description = "Compra de prueba";

                StockLedger transaction = stockLedgerService.recordStockMovement(
                                testProduct.getId(),
                                delta,
                                movementType,
                                description,
                                testUser, 123, java.time.LocalDate.now().plusDays(30));

                assertNotNull(transaction);
                assertEquals(testProduct.getId(), transaction.getProduct().getId());
                assertEquals(0, delta.compareTo(transaction.getQuantityDelta()));
                assertEquals(0, new BigDecimal("150.0").compareTo(transaction.getResultingStock()));
                assertEquals(movementType, transaction.getMovementType());
                assertEquals(description, transaction.getDescription());
                assertEquals(Long.valueOf(1), transaction.getSequenceNumber());
                assertNotNull(transaction.getCurrentHash());
                assertEquals("GENESIS", transaction.getPreviousHash());
                assertTrue(transaction.getVerified());
        }

        @Test
        @Transactional
        @DisplayName("Debe persistir expirationDate en ledger sin afectar la integridad de hash")
        void testRecordStockMovement_PersistsExpirationDateWithoutAffectingHashChain() {
                StockLedger tx = stockLedgerService.recordStockMovement(
                                testProduct.getId(),
                                new BigDecimal("5.0"),
                                MovementType.ENTRADA,
                                "Entrada con caducidad",
                                testUser,
                                77,
                                LocalDate.now().plusDays(30));

                assertNotNull(tx.getExpirationDate());

                jdbcTemplate.update(
                                "UPDATE stock_ledger SET expiration_date = ? WHERE transaction_id = ?",
                                java.sql.Date.valueOf(LocalDate.now().plusDays(40)),
                                tx.getId());

                IntegrityCheckResult integrityCheck = stockLedgerService.verifyChainIntegrity(testProduct.getId());
                assertTrue(integrityCheck.isValid(), "expirationDate no debe participar en el hash criptográfico");
        }

        @Test
        @Transactional
        @DisplayName("Debe consumir lotes en orden FEFO para salidas")
        void testRecordStockMovement_ConsumesBatchesUsingFefo() {
                StockLedger firstEntry = stockLedgerService.recordStockMovement(
                                testProduct.getId(),
                                new BigDecimal("10.0"),
                                MovementType.ENTRADA,
                                "Entrada lote A",
                                testUser,
                                null,
                                LocalDate.now().plusDays(3));

                StockLedger secondEntry = stockLedgerService.recordStockMovement(
                                testProduct.getId(),
                                new BigDecimal("10.0"),
                                MovementType.ENTRADA,
                                "Entrada lote B",
                                testUser,
                                null,
                                LocalDate.now().plusDays(10));

                stockLedgerService.recordStockMovement(
                                testProduct.getId(),
                                new BigDecimal("-12.0"),
                                MovementType.SALIDA,
                                "Consumo FEFO",
                                testUser, null, java.time.LocalDate.now().plusDays(30));

                List<ProductBatch> activeBatches = productBatchRepository.findActiveByProductIdOrderByExpiration(testProduct.getId());

                assertEquals(2, activeBatches.size()); // Batch B + Lote dummy del setUp
                ProductBatch batchB = activeBatches.get(0);
                assertEquals(LocalDate.now().plusDays(10), batchB.getExpirationDate());
                assertEquals(new BigDecimal("8.000"), batchB.getRemainingQuantity());

                long depletedCount = productBatchRepository.findByProductIdAndDepletedFalseOrderByExpirationDateAsc(testProduct.getId()).size();
                assertEquals(2L, depletedCount); // Dummy + Batch B
        }

        @Test
        @Transactional
        @DisplayName("Debe crear snapshot inicial automáticamente")
        void testRecordStockMovement_CreatesSnapshot() {

                BigDecimal delta = new BigDecimal("25.0");

                stockLedgerService.recordStockMovement(
                                testProduct.getId(),
                                delta,
                                MovementType.MODIFICACION,
                                "Test",
                                testUser, null, java.time.LocalDate.now().plusDays(30));

                Optional<StockSnapshot> snapshot = snapshotRepository.findById(testProduct.getId());
                assertTrue(snapshot.isPresent());
                assertEquals(0, new BigDecimal("125.0").compareTo(snapshot.get().getCurrentStock()));
                assertEquals("VALID", snapshot.get().getIntegrityStatus());
                assertEquals(Long.valueOf(1), snapshot.get().getLastSequenceNumber());
        }

        @Test
        @Transactional
        @DisplayName("Debe encadenar múltiples transacciones correctamente")
        void testRecordStockMovement_ChainMultipleTransactions() {

                StockLedger tx1 = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("10.0"), MovementType.ENTRADA, "TX1", testUser, null, java.time.LocalDate.now().plusDays(30));

                StockLedger tx2 = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("-5.0"), MovementType.SALIDA, "TX2", testUser, null, java.time.LocalDate.now().plusDays(30));

                StockLedger tx3 = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("15.0"), MovementType.ENTRADA, "TX3", testUser, null, java.time.LocalDate.now().plusDays(30));

                assertEquals(Long.valueOf(1), tx1.getSequenceNumber());
                assertEquals("GENESIS", tx1.getPreviousHash());

                assertEquals(Long.valueOf(2), tx2.getSequenceNumber());
                assertEquals(tx1.getCurrentHash(), tx2.getPreviousHash());

                assertEquals(Long.valueOf(3), tx3.getSequenceNumber());
                assertEquals(tx2.getCurrentHash(), tx3.getPreviousHash());

                assertEquals(0, new BigDecimal("120.0").compareTo(tx3.getResultingStock()));
        }

        @Test
        @Transactional
        @DisplayName("Debe fallar si el stock resulta negativo")
        void testRecordStockMovement_NegativeStockFails() {

                BigDecimal excessiveDelta = new BigDecimal("-200.0");

                assertThrows(InvalidOperationException.class, () -> {
                        stockLedgerService.recordStockMovement(
                                        testProduct.getId(),
                                        excessiveDelta,
                                        MovementType.SALIDA,
                                        "Intento de salida excesiva",
                                        testUser, null, java.time.LocalDate.now().plusDays(30));
                });
        }

        @Test
        @Transactional
        @DisplayName("Debe permitir salida cuando el stock resultante iguala la reserva activa")
        void testRecordStockMovement_AllowsWhenResultEqualsReserved() {
                StockLedger tx = stockLedgerService.recordStockMovement(
                                testProduct.getId(),
                                new BigDecimal("-5.0"),
                                MovementType.SALIDA,
                                "Salida permitida por igualdad con reserva",
                                testUser,
                                null,
                                LocalDate.now().plusDays(30));

                assertNotNull(tx);
                assertEquals(0, new BigDecimal("95.0").compareTo(tx.getResultingStock()));
        }

        @Test
        @Transactional
        @DisplayName("Debe bloquear salida cuando deja stock por debajo de la reserva activa")
        void testRecordStockMovement_BlocksWhenResultBelowReserved() {
                doThrow(new InvalidOperationException("Violación de la política de disponibilidad."))
                                .when(weeklyPlanStockReservationService)
                                .validateDecrementAgainstActiveReservations(anyInt(), any(BigDecimal.class), any(BigDecimal.class));

                assertThrows(InvalidOperationException.class, () -> stockLedgerService.recordStockMovement(
                                testProduct.getId(),
                                new BigDecimal("-6.0"),
                                MovementType.SALIDA,
                                "Salida no permitida por reserva",
                                testUser,
                                null,
                                LocalDate.now().plusDays(30)));
        }

        @Test
        @Transactional
        @DisplayName("Debe verificar integridad de cadena válida")
        void testVerifyChainIntegrity_ValidChain() {

                for (int i = 1; i <= 5; i++) {
                        stockLedgerService.recordStockMovement(
                                        testProduct.getId(),
                                        new BigDecimal(i * 10),
                                        MovementType.MODIFICACION,
                                        "TX" + i,
                                        testUser, null, java.time.LocalDate.now().plusDays(30));
                }

                IntegrityCheckResult result = stockLedgerService
                                .verifyChainIntegrity(testProduct.getId());

                assertTrue(result.isValid(), "La cadena debería ser válida");
                assertTrue(result.getMessage().contains("íntegra"));
                assertNull(result.getErrors());

                StockSnapshot snapshot = snapshotRepository.findById(testProduct.getId()).orElseThrow();
                assertEquals("VALID", snapshot.getIntegrityStatus());
        }

        @Test
        @Transactional
        @DisplayName("Debe detectar corrupción cuando se modifica el stock manualmente")
        void testVerifyChainIntegrity_DetectsStockCorruption() {

                @SuppressWarnings("unused")
                StockLedger tx1 = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("50.0"), MovementType.ENTRADA, "TX1", testUser, null, java.time.LocalDate.now().plusDays(30));

                StockLedger tx2 = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("30.0"), MovementType.ENTRADA, "TX2", testUser, null, java.time.LocalDate.now().plusDays(30));

                entityManager.flush();
                entityManager.clear();

                jdbcTemplate.update(
                                "UPDATE stock_ledger SET resulting_stock = 9999 WHERE transaction_id = ?",
                                tx2.getId());

                IntegrityCheckResult result = stockLedgerService
                                .verifyChainIntegrity(testProduct.getId());

                assertFalse(result.isValid(), "¡Debería detectar la corrupción!");
                assertTrue(result.getMessage().contains("CORRUPCIÓN DETECTADA"));
                assertNotNull(result.getErrors());
                assertFalse(result.getErrors().isEmpty());

                assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("Hash") || error.contains("corruption") || error.contains("Sequence")));
        }

        @Test
        @Transactional
        @DisplayName("Debe validar cadena tras round-trip de BD con decimales pequeños")
        void testVerifyChainIntegrity_ValidAfterDatabaseRoundTripWithSmallDecimals() {

                stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("0.005"), MovementType.MODIFICACION, "TX1", testUser, null, java.time.LocalDate.now().plusDays(30));

                stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("0.002"), MovementType.MODIFICACION, "TX2", testUser, null, java.time.LocalDate.now().plusDays(30));

                stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("0.003"), MovementType.MODIFICACION, "TX3", testUser, null, java.time.LocalDate.now().plusDays(30));

                stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("0.001"), MovementType.MODIFICACION, "TX4", testUser, null, java.time.LocalDate.now().plusDays(30));

                stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("-0.011"), MovementType.MODIFICACION, "TX5", testUser, null, java.time.LocalDate.now().plusDays(30));

                stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("-2.000"), MovementType.SALIDA, "TX6", testUser, null, java.time.LocalDate.now().plusDays(30));

                stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("1.000"), MovementType.ENTRADA, "TX7", testUser, null, java.time.LocalDate.now().plusDays(30));

                entityManager.flush();
                entityManager.clear();

                IntegrityCheckResult result = stockLedgerService.verifyChainIntegrity(testProduct.getId());

                assertTrue(result.isValid(), "La cadena no debe marcarse como corrupta tras persistir y releer");
                assertNull(result.getErrors());
        }

        @Test
        @Transactional
        @DisplayName("Debe detectar corrupción cuando se modifica la cantidad manualmente")
        void testVerifyChainIntegrity_DetectsQuantityCorruption() {

                StockLedger tx1 = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("20.0"), MovementType.ENTRADA, "TX1", testUser, null, java.time.LocalDate.now().plusDays(30));

                entityManager.flush();
                entityManager.clear();

                jdbcTemplate.update(
                                "UPDATE stock_ledger SET quantity_delta = 5000 WHERE transaction_id = ?",
                                tx1.getId());

                IntegrityCheckResult result = stockLedgerService
                                .verifyChainIntegrity(testProduct.getId());

                assertFalse(result.isValid());
                assertTrue(result.getMessage().contains("CORRUPCIÓN"));
        }

        @Test
        @Transactional
        @DisplayName("Debe detectar si se rompe el encadenamiento (previousHash manipulado)")
        void testVerifyChainIntegrity_DetectsBrokenChain() {

                @SuppressWarnings("unused")
                StockLedger tx1 = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("10.0"), MovementType.ENTRADA, "TX1", testUser, null, java.time.LocalDate.now().plusDays(30));

                StockLedger tx2 = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("10.0"), MovementType.ENTRADA, "TX2", testUser, null, java.time.LocalDate.now().plusDays(30));

                entityManager.flush();
                entityManager.clear();

                jdbcTemplate.update(
                                "UPDATE stock_ledger SET previous_hash = 'FAKE_HASH' WHERE transaction_id = ?",
                                tx2.getId());

                IntegrityCheckResult result = stockLedgerService
                                .verifyChainIntegrity(testProduct.getId());

                assertFalse(result.isValid());
                assertTrue(result.getErrors().stream()
                                .anyMatch(e -> e.contains("previous") || e.contains("chain") || e.contains("mismatch")));
        }

        @Test
        @Transactional
        @DisplayName("Debe detectar si se elimina una transacción de la cadena")
        void testVerifyChainIntegrity_DetectsDeletedTransaction() {

                @SuppressWarnings("unused")
                StockLedger tx1 = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("10.0"), MovementType.ENTRADA, "TX1", testUser, null, java.time.LocalDate.now().plusDays(30));

                StockLedger tx2 = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("10.0"), MovementType.ENTRADA, "TX2", testUser, null, java.time.LocalDate.now().plusDays(30));

                @SuppressWarnings("unused")
                StockLedger tx3 = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("10.0"), MovementType.ENTRADA, "TX3", testUser, null, java.time.LocalDate.now().plusDays(30));

                // Corromper la cadena eliminando una transacción intermedia (TX2) directamente vía JDBC
                List<StockLedger> history = stockLedgerService.getProductHistory(testProduct.getId());
                StockLedger txToDelete = history.get(1); // This should be TX2

                // Primero borrar detalles de lotes y lotes que referencien a esta tx
                jdbcTemplate.update("DELETE FROM stock_ledger_batch_detail WHERE ledger_transaction_id = ?", txToDelete.getId());
                jdbcTemplate.update("DELETE FROM product_batch WHERE ledger_transaction_id = ?", txToDelete.getId());
                jdbcTemplate.update("DELETE FROM stock_ledger WHERE transaction_id = ?", txToDelete.getId());

                IntegrityCheckResult result = stockLedgerService
                                .verifyChainIntegrity(testProduct.getId());

                assertFalse(result.isValid());
        }

        @Test
        @Transactional
        @DisplayName("Debe obtener el historial completo de transacciones")
        void testGetProductHistory() {

                stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("10.0"), MovementType.ENTRADA, "Compra 1", testUser, 1, java.time.LocalDate.now().plusDays(30));
                stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("-5.0"), MovementType.SALIDA, "Venta 1", testUser, 2, java.time.LocalDate.now().plusDays(30));
                stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("20.0"), MovementType.ENTRADA, "Compra 2", testUser, 3, java.time.LocalDate.now().plusDays(30));

                List<StockLedger> history = stockLedgerService.getProductHistory(testProduct.getId());

                assertEquals(3, history.size());
                assertEquals(Long.valueOf(1), history.get(0).getSequenceNumber());
                assertEquals(Long.valueOf(2), history.get(1).getSequenceNumber());
                assertEquals(Long.valueOf(3), history.get(2).getSequenceNumber());

                assertTrue(history.get(0).getSequenceNumber() < history.get(1).getSequenceNumber());
                assertTrue(history.get(1).getSequenceNumber() < history.get(2).getSequenceNumber());
        }

        @Test
        @Transactional
        @DisplayName("Debe obtener el snapshot actual (lectura O(1))")
        void testGetCurrentStock() {

                stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("75.0"), MovementType.ENTRADA, "Test", testUser, null, java.time.LocalDate.now().plusDays(30));

                Optional<StockSnapshot> snapshot = stockLedgerService.getCurrentStock(testProduct.getId());

                assertTrue(snapshot.isPresent());
                assertEquals(0, new BigDecimal("175.0").compareTo(snapshot.get().getCurrentStock()));
                assertEquals("VALID", snapshot.get().getIntegrityStatus());
                assertNotNull(snapshot.get().getLastTransactionHash());
        }

        @Test
        @Transactional
        @DisplayName("Debe sincronizar Product.currentStock con el ledger")
        void testRecordStockMovement_SyncsProductStock() {

                BigDecimal initialStock = testProduct.getCurrentStock();
                BigDecimal delta = new BigDecimal("33.5");

                stockLedgerService.recordStockMovement(
                                testProduct.getId(), delta, MovementType.MODIFICACION, "Test", testUser, null, java.time.LocalDate.now().plusDays(30));

                Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
                assertEquals(0, initialStock.add(delta).compareTo(updatedProduct.getCurrentStock()));
        }

        @Test
        @Transactional
        @DisplayName("Debe verificar todas las cadenas del sistema")
        void testVerifyAllChains() {

                Product product2 = new Product();
                product2.setName("Test Product 2");
                product2.setUnit("L");
                product2.setUnitPrice(new BigDecimal("5.0"));
                product2.setProductCode("LEDGER-TEST-002");
                product2.setCurrentStock(new BigDecimal("50.0"));
 // Required field
                product2 = productRepository.save(product2);

                stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("10.0"), MovementType.ENTRADA, "P1-TX1", testUser, null, java.time.LocalDate.now().plusDays(30));
                stockLedgerService.recordStockMovement(
                                product2.getId(), new BigDecimal("20.0"), MovementType.ENTRADA, "P2-TX1", testUser, null, java.time.LocalDate.now().plusDays(30));

                List<IntegrityCheckResult> results = stockLedgerService.verifyAllChains();

                assertEquals(2, results.size());
                assertTrue(results.stream().allMatch(IntegrityCheckResult::isValid));

                StockSnapshot snapshot1 = snapshotRepository.findById(testProduct.getId()).orElseThrow();
                StockSnapshot snapshot2 = snapshotRepository.findById(product2.getId()).orElseThrow();

                assertEquals("VALID", snapshot1.getIntegrityStatus());
                assertEquals("VALID", snapshot2.getIntegrityStatus());
                assertNotNull(snapshot1.getLastVerified());
                assertNotNull(snapshot2.getLastVerified());
        }

        @Test
        @Transactional
        @DisplayName("Debe calcular el hash consistentemente")
        void testHashCalculation_Consistency() {

                StockLedger tx1 = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("10.0"), MovementType.ENTRADA, "Test", testUser, null, java.time.LocalDate.now().plusDays(30));

                String hash = tx1.getCurrentHash();

                assertNotNull(hash);
                assertEquals(64, hash.length(), "SHA-256 en hexadecimal debe tener 64 caracteres");
                assertFalse(hash.contains(" "), "El hash no debe contener espacios");
                assertTrue(hash.matches("[a-f0-9]+"), "El hash debe ser hexadecimal");
        }

        @Test
        @Transactional
        @DisplayName("Debe marcar transacciones como verificadas")
        void testVerifyChainIntegrity_UpdatesVerificationStatus() {

                stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("25.0"), MovementType.ENTRADA, "Test", testUser, null, java.time.LocalDate.now().plusDays(30));

                stockLedgerService.verifyChainIntegrity(testProduct.getId());

                List<StockLedger> transactions = stockLedgerService.getProductHistory(testProduct.getId());
                assertTrue(transactions.stream().allMatch(StockLedger::getVerified));
        }

        @Test
        @Transactional
        @DisplayName("Debe retirar un lote caducado correctamente actualizando stock y ledger")
        void testWithdrawExpiredBatch_Success() {
                // 1. Crear un lote caducado
                ProductBatch expiredBatch = ProductBatch.builder()
                                .product(testProduct)
                                .initialQuantity(new BigDecimal("10.000"))
                                .remainingQuantity(new BigDecimal("10.000"))
                                .expirationDate(LocalDate.now().minusDays(5))
                                .receivedAt(java.time.LocalDateTime.now().minusDays(10))
                                .depleted(false)
                                .build();
                expiredBatch = productBatchRepository.saveAndFlush(expiredBatch);
                
                BigDecimal initialStock = productRepository.findById(testProduct.getId()).get().getCurrentStock();

                // 2. Ejecutar retirada
                stockLedgerService.withdrawExpiredBatch(expiredBatch.getId());

                // 3. Verificar resultados
                ProductBatch updatedBatch = productBatchRepository.findById(expiredBatch.getId()).get();
                assertTrue(updatedBatch.isDepleted());
                assertEquals(0, BigDecimal.ZERO.compareTo(updatedBatch.getRemainingQuantity()));

                Product updatedProduct = productRepository.findById(testProduct.getId()).get();
                assertEquals(0, initialStock.subtract(new BigDecimal("10.000")).compareTo(updatedProduct.getCurrentStock()));

                List<StockLedger> history = stockLedgerService.getProductHistory(testProduct.getId());
                StockLedger lastTx = history.get(history.size() - 1);
                assertEquals(MovementType.MERMA, lastTx.getMovementType());
                assertEquals(0, new BigDecimal("-10.000").compareTo(lastTx.getQuantityDelta()));
        }
}
