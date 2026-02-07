package com.economato.inventory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.exception.InvalidOperationException;
import com.economato.inventory.model.MovementType;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.StockLedger;
import com.economato.inventory.model.StockSnapshot;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.StockLedgerRepository;
import com.economato.inventory.repository.StockSnapshotRepository;
import com.economato.inventory.service.StockLedgerService;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class StockLedgerServiceIntegrationTest {

        @Autowired
        private StockLedgerService stockLedgerService;

        @Autowired
        private StockLedgerRepository ledgerRepository;

        @Autowired
        private StockSnapshotRepository snapshotRepository;

        @Autowired
        private ProductRepository productRepository;

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

                testProduct = new Product();
                testProduct.setName("Test Product - Ledger");
                testProduct.setType("Ingrediente");
                testProduct.setUnit("KG");
                testProduct.setUnitPrice(new BigDecimal("10.50"));
                testProduct.setProductCode("LEDGER-TEST-001");
                testProduct.setCurrentStock(new BigDecimal("100.0"));
                testProduct = productRepository.saveAndFlush(testProduct);

                testUser = null;
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
                                testUser,
                                123);

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
        @DisplayName("Debe crear snapshot inicial automáticamente")
        void testRecordStockMovement_CreatesSnapshot() {

                BigDecimal delta = new BigDecimal("25.0");

                stockLedgerService.recordStockMovement(
                                testProduct.getId(),
                                delta,
                                MovementType.AJUSTE,
                                "Test",
                                testUser,
                                null);

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
                                testProduct.getId(), new BigDecimal("10.0"), MovementType.ENTRADA, "TX1", testUser,
                                null);

                StockLedger tx2 = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("-5.0"), MovementType.SALIDA, "TX2", testUser,
                                null);

                StockLedger tx3 = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("15.0"), MovementType.ENTRADA, "TX3", testUser,
                                null);

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
                                        testUser,
                                        null);
                });
        }

        @Test
        @Transactional
        @DisplayName("Debe verificar integridad de cadena válida")
        void testVerifyChainIntegrity_ValidChain() {

                for (int i = 1; i <= 5; i++) {
                        stockLedgerService.recordStockMovement(
                                        testProduct.getId(),
                                        new BigDecimal(i * 10),
                                        MovementType.AJUSTE,
                                        "TX" + i,
                                        testUser,
                                        null);
                }

                StockLedgerService.IntegrityCheckResult result = stockLedgerService
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

                StockLedger tx1 = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("50.0"), MovementType.ENTRADA, "TX1", testUser,
                                null);

                StockLedger tx2 = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("30.0"), MovementType.ENTRADA, "TX2", testUser,
                                null);

                entityManager.flush();
                entityManager.clear();

                jdbcTemplate.update(
                                "UPDATE stock_ledger SET resulting_stock = 9999 WHERE transaction_id = ?",
                                tx2.getId());

                StockLedgerService.IntegrityCheckResult result = stockLedgerService
                                .verifyChainIntegrity(testProduct.getId());

                assertFalse(result.isValid(), "¡Debería detectar la corrupción!");
                assertTrue(result.getMessage().contains("CORRUPCIÓN DETECTADA"));
                assertNotNull(result.getErrors());
                assertFalse(result.getErrors().isEmpty());

                String errorMessage = String.join(", ", result.getErrors());
                assertTrue(errorMessage.contains("Hash corrupto") || errorMessage.contains("TX#"));

                System.out.println("CORRUPCIÓN DETECTADA:");
                result.getErrors().forEach(System.out::println);
        }

        @Test
        @Transactional
        @DisplayName("Debe detectar corrupción cuando se modifica la cantidad manualmente")
        void testVerifyChainIntegrity_DetectsQuantityCorruption() {

                StockLedger tx1 = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("20.0"), MovementType.ENTRADA, "TX1", testUser,
                                null);

                entityManager.flush();
                entityManager.clear();

                jdbcTemplate.update(
                                "UPDATE stock_ledger SET quantity_delta = 5000 WHERE transaction_id = ?",
                                tx1.getId());

                StockLedgerService.IntegrityCheckResult result = stockLedgerService
                                .verifyChainIntegrity(testProduct.getId());

                assertFalse(result.isValid());
                assertTrue(result.getMessage().contains("CORRUPCIÓN"));

                System.out.println("INTENTO DE ROBO DETECTADO:");
                result.getErrors().forEach(System.out::println);
        }

        @Test
        @Transactional
        @DisplayName("Debe detectar si se rompe el encadenamiento (previousHash manipulado)")
        void testVerifyChainIntegrity_DetectsBrokenChain() {

                StockLedger tx1 = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("10.0"), MovementType.ENTRADA, "TX1", testUser,
                                null);

                StockLedger tx2 = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("10.0"), MovementType.ENTRADA, "TX2", testUser,
                                null);

                entityManager.flush();
                entityManager.clear();

                jdbcTemplate.update(
                                "UPDATE stock_ledger SET previous_hash = 'FAKE_HASH' WHERE transaction_id = ?",
                                tx2.getId());

                StockLedgerService.IntegrityCheckResult result = stockLedgerService
                                .verifyChainIntegrity(testProduct.getId());

                assertFalse(result.isValid());
                assertTrue(result.getErrors().stream()
                                .anyMatch(e -> e.contains("previousHash incorrecto")));

                System.out.println("CADENA ROTA DETECTADA:");
                result.getErrors().forEach(System.out::println);
        }

        @Test
        @Transactional
        @DisplayName("Debe detectar si se elimina una transacción de la cadena")
        void testVerifyChainIntegrity_DetectsDeletedTransaction() {

                StockLedger tx1 = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("10.0"), MovementType.ENTRADA, "TX1", testUser,
                                null);

                StockLedger tx2 = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("10.0"), MovementType.ENTRADA, "TX2", testUser,
                                null);

                StockLedger tx3 = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("10.0"), MovementType.ENTRADA, "TX3", testUser,
                                null);

                jdbcTemplate.update("DELETE FROM stock_ledger WHERE transaction_id = ?", tx2.getId());

                StockLedgerService.IntegrityCheckResult result = stockLedgerService
                                .verifyChainIntegrity(testProduct.getId());

                assertFalse(result.isValid());

                System.out.println("TRANSACCIÓN ELIMINADA DETECTADA:");
                result.getErrors().forEach(System.out::println);
        }

        @Test
        @Transactional
        @DisplayName("Debe obtener el historial completo de transacciones")
        void testGetProductHistory() {

                stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("10.0"), MovementType.ENTRADA, "Compra 1", testUser,
                                1);
                stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("-5.0"), MovementType.SALIDA, "Venta 1", testUser,
                                2);
                stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("20.0"), MovementType.ENTRADA, "Compra 2", testUser,
                                3);

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
                                testProduct.getId(), new BigDecimal("75.0"), MovementType.ENTRADA, "Test", testUser,
                                null);

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
                                testProduct.getId(), delta, MovementType.AJUSTE, "Test", testUser, null);

                Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
                assertEquals(0, initialStock.add(delta).compareTo(updatedProduct.getCurrentStock()));
        }

        @Test
        @Transactional
        @DisplayName("Debe verificar todas las cadenas del sistema")
        void testVerifyAllChains() {

                Product product2 = new Product();
                product2.setName("Test Product 2");
                product2.setType("Ingrediente");
                product2.setUnit("L");
                product2.setUnitPrice(new BigDecimal("5.0"));
                product2.setProductCode("LEDGER-TEST-002");
                product2.setCurrentStock(new BigDecimal("50.0"));
                product2 = productRepository.save(product2);

                stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("10.0"), MovementType.ENTRADA, "P1-TX1", testUser,
                                null);
                stockLedgerService.recordStockMovement(
                                product2.getId(), new BigDecimal("20.0"), MovementType.ENTRADA, "P2-TX1", testUser,
                                null);

                List<StockLedgerService.IntegrityCheckResult> results = stockLedgerService.verifyAllChains();

                assertEquals(2, results.size());
                assertTrue(results.stream().allMatch(StockLedgerService.IntegrityCheckResult::isValid));

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
                                testProduct.getId(), new BigDecimal("10.0"), MovementType.ENTRADA, "Test", testUser,
                                null);

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
                                testProduct.getId(), new BigDecimal("25.0"), MovementType.ENTRADA, "Test", testUser,
                                null);

                stockLedgerService.verifyChainIntegrity(testProduct.getId());

                List<StockLedger> transactions = stockLedgerService.getProductHistory(testProduct.getId());
                assertTrue(transactions.stream().allMatch(StockLedger::getVerified));
        }

        @Test
        @Transactional
        @DisplayName("Debe restablecer el historial completo de un producto")
        void testResetProductLedger() {

                stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("50.0"), MovementType.ENTRADA, "TX1", testUser,
                                null);
                stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("30.0"), MovementType.ENTRADA, "TX2", testUser,
                                null);
                stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("-20.0"), MovementType.SALIDA, "TX3", testUser,
                                null);

                List<StockLedger> historyBefore = stockLedgerService.getProductHistory(testProduct.getId());
                assertEquals(3, historyBefore.size(), "Debe haber 3 transacciones");
                assertTrue(snapshotRepository.existsById(testProduct.getId()), "El snapshot debe existir");

                String result = stockLedgerService.resetProductLedger(testProduct.getId());

                List<StockLedger> historyAfter = stockLedgerService.getProductHistory(testProduct.getId());
                assertEquals(0, historyAfter.size(), "No debe haber transacciones");
                assertFalse(snapshotRepository.existsById(testProduct.getId()), "El snapshot debe estar borrado");
                assertTrue(result.contains("3 transacciones eliminadas"),
                                "El mensaje debe confirmar 3 transacciones eliminadas");

                System.out.println("HISTORIAL RESTABLECIDO: " + result);
        }

        @Test
        @Transactional
        @DisplayName("Debe permitir crear nuevas transacciones después del reseteo")
        void testResetProductLedger_AllowsNewTransactions() {

                stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("100.0"), MovementType.ENTRADA, "Old TX", testUser,
                                null);

                stockLedgerService.resetProductLedger(testProduct.getId());

                StockLedger newTx = stockLedgerService.recordStockMovement(
                                testProduct.getId(), new BigDecimal("50.0"), MovementType.ENTRADA, "New TX", testUser,
                                null);

                assertNotNull(newTx);
                assertEquals(1L, newTx.getSequenceNumber(), "Debe empezar en secuencia 1");
                assertEquals("GENESIS", newTx.getPreviousHash(), "Debe tener previousHash GENESIS");

                StockLedgerService.IntegrityCheckResult integrity = stockLedgerService
                                .verifyChainIntegrity(testProduct.getId());
                assertTrue(integrity.isValid(), "La nueva cadena debe ser válida");

                System.out.println("Nueva cadena válida después del reseteo");
        }
}
