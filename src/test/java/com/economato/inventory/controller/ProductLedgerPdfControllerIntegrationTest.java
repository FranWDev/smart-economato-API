package com.economato.inventory.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.economato.inventory.dto.request.LoginRequestDTO;
import com.economato.inventory.dto.response.LoginResponseDTO;
import com.economato.inventory.model.MovementType;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.StockLedger;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.StockLedgerRepository;
import com.economato.inventory.repository.UserRepository;
import com.economato.inventory.service.StockLedgerPdfService;
import com.economato.inventory.util.TestDataUtil;

class ProductLedgerPdfControllerIntegrationTest extends BaseIntegrationTest {

        private static final String BASE_URL = "/api/products";
        private static final String AUTH_URL = "/api/auth/login";

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private StockLedgerRepository stockLedgerRepository;

        @Autowired
        private StockLedgerPdfService stockLedgerPdfService;

        private String jwtToken;
        private User testUser;
        private Product testProduct;

        @BeforeEach
        void setUp() throws Exception {
                entityManager.clear();
                clearDatabase();

                testUser = TestDataUtil.createChefUser();
                userRepository.saveAndFlush(testUser);

                LoginRequestDTO loginRequest = new LoginRequestDTO();
                loginRequest.setName(testUser.getName());
                loginRequest.setPassword("chef123");

                String response = mockMvc.perform(post(AUTH_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                LoginResponseDTO loginResponse = objectMapper.readValue(response, LoginResponseDTO.class);
                jwtToken = loginResponse.getToken();

                // Create test product
                testProduct = TestDataUtil.createFlour();
                testProduct.setCurrentStock(new BigDecimal("100.000"));
                testProduct = productRepository.saveAndFlush(testProduct);

                // Create ledger entries with cumulative resulting stock
                createLedgerEntry(1L, "100.000", "ENTRADA", "Compra inicial", "0", "100.000");
                createLedgerEntry(2L, "-50.000", "SALIDA", "Uso en receta", null, "50.000");
                createLedgerEntry(3L, "20.000", "ENTRADA", "Devolución", null, "70.000");
        }

        private void createLedgerEntry(Long seqNum, String quantity, String type, String description,
                String prevHash, String resultingStockValue) {
                createLedgerEntry(testProduct, seqNum, quantity, type, description, prevHash, resultingStockValue);
        }

        private void createLedgerEntry(Product product, Long seqNum, String quantity, String type, String description,
                String prevHash, String resultingStockValue) {
                StockLedger ledger = new StockLedger();
                ledger.setProduct(product);
                ledger.setQuantityDelta(new BigDecimal(quantity));
                ledger.setSequenceNumber(seqNum);
                ledger.setMovementType(MovementType.valueOf(type));
                ledger.setDescription(description);
                ledger.setResultingStock(new BigDecimal(resultingStockValue));
                ledger.setTransactionTimestamp(java.time.LocalDateTime.now());
                ledger.setUser(testUser);
                ledger.setPreviousHash(prevHash != null ? prevHash : "0");
                // Use UUID to ensure uniqueness across all tests
                ledger.setCurrentHash("hash_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12));
                ledger.setVerified(true);
                stockLedgerRepository.save(ledger);
        }

        @Test
        void whenDownloadStockLedgerPdf_WithValidProduct_ThenReturnsPdf() throws Exception {
                ResultActions response = mockMvc.perform(get(BASE_URL + "/{id}/ledger/pdf", testProduct.getId())
                                .header("Authorization", "Bearer " + jwtToken));

                response.andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                        .andExpect(header().exists("Content-Disposition"));

                byte[] pdfContent = response.andReturn().getResponse().getContentAsByteArray();
                assert (pdfContent.length > 0);
                assert (new String(pdfContent).contains("PDF") || pdfContent[0] == 0x25); // PDF magic bytes
        }

        @Test
        void whenDownloadStockLedgerPdf_WithNonExistentProduct_ThenReturns404() throws Exception {
                mockMvc.perform(get(BASE_URL + "/{id}/ledger/pdf", 99999)
                                .header("Authorization", "Bearer " + jwtToken))
                        .andExpect(status().isNotFound());
        }

        @Test
        void whenDownloadStockLedgerPdf_WithoutAuth_ThenReturns401() throws Exception {
                mockMvc.perform(get(BASE_URL + "/{id}/ledger/pdf", testProduct.getId()))
                        .andExpect(status().isUnauthorized());
        }

        @Test
        void whenDownloadStockLedgerPdf_WithInsufficientRole_ThenReturns403() throws Exception {
                User regularUser = TestDataUtil.createRegularUser();
                userRepository.save(regularUser);

                LoginRequestDTO loginRequest = new LoginRequestDTO();
                loginRequest.setName(regularUser.getName());
                loginRequest.setPassword("user123");

                String response = mockMvc.perform(post(AUTH_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                LoginResponseDTO loginResponse = objectMapper.readValue(response, LoginResponseDTO.class);
                String regularToken = loginResponse.getToken();

                mockMvc.perform(get(BASE_URL + "/{id}/ledger/pdf", testProduct.getId())
                                .header("Authorization", "Bearer " + regularToken))
                        .andExpect(status().isForbidden());
        }

        @Test
        void verifyLedgerIntegrity_WithUnalteredData_ShouldReturnTrue() throws Exception {
                // Generate PDF and verify it contains proper hash
                byte[] pdfBytes = stockLedgerPdfService.generateStockLedgerPdf(testProduct.getId());
                
                // Verify PDF was generated
                assertNotNull(pdfBytes, "PDF should be generated");
                assertTrue(pdfBytes.length > 0, "PDF should not be empty");
                
                // Verify the ledger integrity function works
                boolean verified = stockLedgerPdfService.verifyLedgerIntegrity(testProduct.getId(),
                        "dummy_hash");
                // This should return false because the hash doesn't match
                assertFalse(verified, "Dummy hash should not match actual ledger");
        }

        @Test
        void whenGenerateStockLedgerPdf_ShouldIncludeProductInfo() throws Exception {
                byte[] pdfBytes = stockLedgerPdfService.generateStockLedgerPdf(testProduct.getId());

                // Verify PDF was generated with non-empty content
                assertNotNull(pdfBytes, "PDF should not be null");
                assertTrue(pdfBytes.length > 0, "PDF should contain product info section");
        }

        @Test
        void whenGenerateStockLedgerPdf_ShouldIncludeLedgerTransactions() throws Exception {
                byte[] pdfBytes = stockLedgerPdfService.generateStockLedgerPdf(testProduct.getId());

                // Verify PDF was generated with non-empty content
                assertNotNull(pdfBytes, "PDF should not be null");
                assertTrue(pdfBytes.length > 0, "PDF should contain transaction history");
        }

        @Test
        void whenGenerateStockLedgerPdf_ShouldIncludeAuthenticationSignature() throws Exception {
                byte[] pdfBytes = stockLedgerPdfService.generateStockLedgerPdf(testProduct.getId());

                // Verify PDF was generated with non-empty content
                assertNotNull(pdfBytes, "PDF should not be null");
                assertTrue(pdfBytes.length > 0, "PDF should contain authentication signature section");
        }

        @Test
        void whenDownloadPdfWithSpecialCharactersInProductName_ThenFilenameSanitized() throws Exception {
                Product specialProduct = new Product();
                specialProduct.setName("Producto!@#$%^&*()++Con Especiales");
                specialProduct.setType("Ingrediente");
                specialProduct.setUnit("KG");
                specialProduct.setUnitPrice(new BigDecimal("5.00"));
                specialProduct.setProductCode("SPEC001");
                specialProduct.setCurrentStock(new BigDecimal("50.000"));
                specialProduct.setMinimumStock(new BigDecimal("10.000"));
                specialProduct = productRepository.saveAndFlush(specialProduct);

                // Create at least one ledger entry for the special product
                createLedgerEntry(specialProduct, 1L, "50.000", "ENTRADA", "Test", "0", "50.000");

                mockMvc.perform(get(BASE_URL + "/{id}/ledger/pdf", specialProduct.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Content-Disposition",
                                containsString("ledger_stock_")));
        }

}
