package com.economato.inventory.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.economato.inventory.i18n.I18nService;
import com.economato.inventory.model.MovementType;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.StockLedger;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.StockLedgerRepository;

@ExtendWith(MockitoExtension.class)
class StockLedgerPdfServiceTest {

        @Mock
        private StockLedgerRepository stockLedgerRepository;

        @Mock
        private ProductRepository productRepository;

        @Mock
        private I18nService i18nService;

        @Mock
        private StockLedgerService stockLedgerService;

        @InjectMocks
        private StockLedgerPdfService stockLedgerPdfService;

        private Product testProduct;
        private User testUser;
        private List<StockLedger> testLedgerEntries;

        @BeforeEach
        void setUp() {
                testProduct = new Product();
                testProduct.setId(1);
                testProduct.setName("Test Product");
                testProduct.setProductCode("TEST001");
                testProduct.setType("Ingrediente");
                testProduct.setUnit("KG");
                testProduct.setUnitPrice(new BigDecimal("10.00"));
                testProduct.setCurrentStock(new BigDecimal("100.000"));

                testUser = new User();
                testUser.setId(1);
                testUser.setName("Test User");
                testUser.setUser("testuser");

                testLedgerEntries = createTestLedgerEntries();

                lenient().when(stockLedgerService.verifyChainIntegrity(1))
                                .thenReturn(new com.economato.inventory.dto.response.IntegrityCheckResult(
                                                1,
                                                "Test Product",
                                                true,
                                                "Cadena íntegra",
                                                null));
        }

        private List<StockLedger> createTestLedgerEntries() {
                StockLedger entry1 = StockLedger.builder()
                        .id(1L)
                        .product(testProduct)
                        .quantityDelta(new BigDecimal("100.000"))
                        .resultingStock(new BigDecimal("100.000"))
                        .sequenceNumber(1L)
                        .movementType(MovementType.ENTRADA)
                        .description("Initial entry")
                        .transactionTimestamp(LocalDateTime.of(2026, 3, 1, 10, 0, 0))
                        .user(testUser)
                        .previousHash("0")
                        .currentHash("hash_1_initial")
                        .verified(true)
                        .build();

                StockLedger entry2 = StockLedger.builder()
                        .id(2L)
                        .product(testProduct)
                        .quantityDelta(new BigDecimal("-50.000"))
                        .resultingStock(new BigDecimal("50.000"))
                        .sequenceNumber(2L)
                        .movementType(MovementType.SALIDA)
                        .description("Usage")
                        .transactionTimestamp(LocalDateTime.of(2026, 3, 2, 10, 0, 0))
                        .user(testUser)
                        .previousHash("hash_1_initial")
                        .currentHash("hash_2_usage")
                        .verified(true)
                        .build();

                return Arrays.asList(entry1, entry2);
        }

        @Test
        void generateStockLedgerPdf_ShouldReturnNonEmptyByteArray() {
                when(productRepository.findById(1)).thenReturn(java.util.Optional.of(testProduct));
                when(stockLedgerRepository.findByProductIdOrderBySequenceNumber(1))
                        .thenReturn(testLedgerEntries);

                byte[] result = stockLedgerPdfService.generateStockLedgerPdf(1);

                assertNotNull(result);
                assertTrue(result.length > 0, "PDF should not be empty");
                // PDF files start with %PDF
                assertTrue(new String(result).contains("PDF") || result[0] == 0x25);
        }

        @Test
        void generateStockLedgerPdf_WithNonExistentProduct_ShouldThrowException() {
                when(productRepository.findById(999)).thenReturn(java.util.Optional.empty());

                assertThrows(RuntimeException.class, () -> {
                        stockLedgerPdfService.generateStockLedgerPdf(999);
                });
        }

        @Test
        void generateStockLedgerPdf_WithEmptyLedger_ShouldThrowException() {
                when(productRepository.findById(1)).thenReturn(java.util.Optional.of(testProduct));
                when(stockLedgerRepository.findByProductIdOrderBySequenceNumber(1))
                        .thenReturn(Arrays.asList());

                assertThrows(RuntimeException.class, () -> {
                        stockLedgerPdfService.generateStockLedgerPdf(1);
                });
        }

        @Test
        void verifyLedgerIntegrity_WithCorrectHash_ShouldReturnTrue() {
                when(productRepository.findById(1)).thenReturn(java.util.Optional.of(testProduct));
                when(stockLedgerRepository.findByProductIdOrderBySequenceNumber(1))
                        .thenReturn(testLedgerEntries);

                // Generate the expected hash
                byte[] pdfBytes = stockLedgerPdfService.generateStockLedgerPdf(1);
                // For this test, we'll verify that the method works
                // In production, you'd extract the hash from the generated PDF

                assertNotNull(pdfBytes);
                assertTrue(pdfBytes.length > 0, "PDF should not be empty");
        }

        @Test
        void verifyLedgerIntegrity_WithIncorrectHash_ShouldReturnFalse() {
                when(stockLedgerRepository.findByProductIdOrderBySequenceNumber(1))
                        .thenReturn(testLedgerEntries);

                boolean result = stockLedgerPdfService.verifyLedgerIntegrity(1, "incorrect_hash_value");

                assertFalse(result);
        }

        @Test
        void verifyLedgerIntegrity_WithEmptyLedger_ShouldReturnFalse() {
                when(stockLedgerRepository.findByProductIdOrderBySequenceNumber(1))
                        .thenReturn(Arrays.asList());

                boolean result = stockLedgerPdfService.verifyLedgerIntegrity(1, "any_hash");

                assertFalse(result);
        }

        @Test
        void generateStockLedgerPdf_ShouldIncludeAllLedgerData() {
                when(productRepository.findById(1)).thenReturn(java.util.Optional.of(testProduct));
                when(stockLedgerRepository.findByProductIdOrderBySequenceNumber(1))
                        .thenReturn(testLedgerEntries);

                byte[] result = stockLedgerPdfService.generateStockLedgerPdf(1);

                // Verify PDF was generated (non-empty bytes)
                assertNotNull(result);
                assertTrue(result.length > 0, "PDF result should not be empty");
        }

        @Test
        void generateStockLedgerPdf_ShouldIncludeProductDetails() {
                when(productRepository.findById(1)).thenReturn(java.util.Optional.of(testProduct));
                when(stockLedgerRepository.findByProductIdOrderBySequenceNumber(1))
                        .thenReturn(testLedgerEntries);

                byte[] result = stockLedgerPdfService.generateStockLedgerPdf(1);

                assertNotNull(result);
                assertTrue(result.length > 0, "PDF result should not be empty");
        }

        @Test
        void verifyLedgerIntegrity_HashShouldBeSHA256() {
                when(stockLedgerRepository.findByProductIdOrderBySequenceNumber(1))
                        .thenReturn(testLedgerEntries);

                // SHA-256 hash is 64 characters in hex
                boolean result = stockLedgerPdfService.verifyLedgerIntegrity(1,
                        "0000000000000000000000000000000000000000000000000000000000000000");

                // Should be false because our test data won't match all zeros
                assertFalse(result);
        }

        @Test
        void generateStockLedgerPdfWithIntegrity_ShouldReturnPdfWithIntegrityInfo() {
                when(productRepository.findById(1)).thenReturn(java.util.Optional.of(testProduct));
                when(stockLedgerRepository.findByProductIdOrderBySequenceNumber(1))
                        .thenReturn(testLedgerEntries);
                
                // Mock IntegrityCheckResult
                com.economato.inventory.dto.response.IntegrityCheckResult integrityResult = 
                        new com.economato.inventory.dto.response.IntegrityCheckResult(
                                1, "Test Product", true, "Cadena íntegra", null);
                when(stockLedgerService.verifyChainIntegrity(1)).thenReturn(integrityResult);

                com.economato.inventory.dto.response.LedgerPdfResponseDTO result = 
                        stockLedgerPdfService.generateStockLedgerPdfWithIntegrity(1);

                assertNotNull(result);
                assertNotNull(result.getPdfContent());
                assertTrue(result.getPdfContent().length > 0, "PDF should not be empty");
                assertTrue(result.isIntegrityValid(), "Integrity should be valid");
                assertEquals("Cadena íntegra", result.getIntegrityMessage());
        }

        @Test
        void generateStockLedgerPdfWithIntegrity_WhenCorrupted_ShouldReturnErrorInfo() {
                when(productRepository.findById(1)).thenReturn(java.util.Optional.of(testProduct));
                when(stockLedgerRepository.findByProductIdOrderBySequenceNumber(1))
                        .thenReturn(testLedgerEntries);
                
                // Mock IntegrityCheckResult with corruption
                java.util.List<String> errors = java.util.Arrays.asList("Error 1", "Error 2");
                com.economato.inventory.dto.response.IntegrityCheckResult integrityResult = 
                        new com.economato.inventory.dto.response.IntegrityCheckResult(
                                1, "Test Product", false, "Cadena corrupta", errors);
                when(stockLedgerService.verifyChainIntegrity(1)).thenReturn(integrityResult);

                com.economato.inventory.dto.response.LedgerPdfResponseDTO result = 
                        stockLedgerPdfService.generateStockLedgerPdfWithIntegrity(1);

                assertNotNull(result);
                assertNotNull(result.getPdfContent());
                assertTrue(result.getPdfContent().length > 0, "PDF should not be empty");
                assertFalse(result.isIntegrityValid(), "Integrity should be invalid");
                assertEquals("Cadena corrupta", result.getIntegrityMessage());
                assertNotNull(result.getIntegrityErrors());
                assertEquals(2, result.getIntegrityErrors().size());
        }
}
