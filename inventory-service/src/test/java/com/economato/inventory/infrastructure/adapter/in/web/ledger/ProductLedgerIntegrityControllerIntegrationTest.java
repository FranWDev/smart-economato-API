package com.economato.inventory.infrastructure.adapter.in.web.ledger;
import com.economato.inventory.infrastructure.adapter.in.web.shared.BaseIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.economato.inventory.application.dto.shared.request.LoginRequestDTO;
import com.economato.inventory.application.dto.shared.response.LoginResponseDTO;
import com.economato.inventory.domain.model.shared.MovementType;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.ledger.StockLedger;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ledger.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.shared.TestDataUtil;

class ProductLedgerIntegrityControllerIntegrationTest extends BaseIntegrationTest {

        private static final String BASE_URL = "/api/products";

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private StockLedgerRepository stockLedgerRepository;

        @Autowired
        private UserRepository userRepository;

        private Product testProduct1;
        private Product testProduct2;
        private Product testProductNoLedger;
        private User testUser;
        private String jwtToken;

        @BeforeEach
        void setUp() throws Exception {
                // Limpiar base de datos
                clearDatabase();

                // Crear usuario Chef para autenticación
                User chefUser = TestDataUtil.createChefUser();
                testUser = userRepository.saveAndFlush(chefUser);

                // Autenticar
                LoginRequestDTO loginRequest = new LoginRequestDTO(chefUser.getUser(), "chef123");
                String response = mockMvc
                                .perform(post("/api/auth/login")
                                                .contentType("application/json")
                                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                LoginResponseDTO loginResponse = objectMapper.readValue(response, LoginResponseDTO.class);
                jwtToken = loginResponse.getToken();

                // Crear productos de prueba
                testProduct1 = new Product();
                testProduct1.setName("Producto con Ledger 1");
                testProduct1.setUnit("KG");
                testProduct1.setUnitPrice(new BigDecimal("10.00"));
                testProduct1.setProductCode("LEDGER001");
                testProduct1.setCurrentStock(new BigDecimal("100.000"));
                testProduct1 = productRepository.saveAndFlush(testProduct1);

                testProduct2 = new Product();
                testProduct2.setName("Producto con Ledger 2");
                testProduct2.setUnit("Litros");
                testProduct2.setUnitPrice(new BigDecimal("5.00"));
                testProduct2.setProductCode("LEDGER002");
                testProduct2.setCurrentStock(new BigDecimal("50.000"));
                testProduct2 = productRepository.saveAndFlush(testProduct2);

                testProductNoLedger = new Product();
                testProductNoLedger.setName("Producto sin Ledger");
                testProductNoLedger.setUnit("KG");
                testProductNoLedger.setUnitPrice(new BigDecimal("15.00"));
                testProductNoLedger.setProductCode("NOLEDGER001");
                testProductNoLedger.setCurrentStock(new BigDecimal("0.000"));
                testProductNoLedger = productRepository.saveAndFlush(testProductNoLedger);

                // Crear entradas de ledger para producto 1
                createLedgerEntry(testProduct1, 1L, "100.000", "ENTRADA", "Entrada inicial", "0", "100.000");
                createLedgerEntry(testProduct1, 2L, "-50.000", "SALIDA", "Venta", null, "50.000");

                // Crear entradas de ledger para producto 2
                createLedgerEntry(testProduct2, 1L, "50.000", "ENTRADA", "Entrada inicial", "0", "50.000");
        }

        private void createLedgerEntry(Product product, Long seqNum, String quantity, String type, String description,
                        String prevHash, String resultingStockValue) {
                StockLedger ledger = StockLedger.builder()
                        .product(product)
                        .quantityDelta(new BigDecimal(quantity))
                        .sequenceNumber(seqNum)
                        .movementType(MovementType.valueOf(type))
                        .description(description)
                        .resultingStock(new BigDecimal(resultingStockValue))
                        .transactionTimestamp(java.time.LocalDateTime.now())
                        .user(testUser)
                        .previousHash(prevHash != null ? prevHash : "0")
                        .currentHash("hash_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                        .verified(true)
                        .build();
                stockLedgerRepository.save(ledger);
        }

        @Test
        void whenGetProductsWithLedger_ThenReturnPaginatedProducts() throws Exception {
                mockMvc.perform(get(BASE_URL + "/with-ledger")
                                .header("Authorization", "Bearer " + jwtToken))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content", hasSize(2)))
                        .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(testProduct1.getId(), testProduct2.getId())))
                        .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        void whenGetProductsWithLedger_FilterByName_ThenReturnMatchingProducts() throws Exception {
                mockMvc.perform(get(BASE_URL + "/with-ledger")
                                .param("name", "Ledger 1")
                                .header("Authorization", "Bearer " + jwtToken))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content", hasSize(1)))
                        .andExpect(jsonPath("$.content[0].id").value(testProduct1.getId()))
                        .andExpect(jsonPath("$.content[0].name").value("Producto con Ledger 1"));
        }

        @Test
        void whenGetProductsWithLedger_EmptyName_ThenReturnPaginatedProducts() throws Exception {
                mockMvc.perform(get(BASE_URL + "/with-ledger")
                                .param("name", "")
                                .header("Authorization", "Bearer " + jwtToken))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content", hasSize(2)))
                        .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        void whenGetProductsWithLedger_WithoutAuth_ThenUnauthorized() throws Exception {
                mockMvc.perform(get(BASE_URL + "/with-ledger"))
                        .andExpect(status().isUnauthorized());
        }

        @Test
        void whenVerifyLedgerIntegrity_ThenReturnIntegrityResults() throws Exception {
                mockMvc.perform(get(BASE_URL + "/ledger-integrity")
                                .header("Authorization", "Bearer " + jwtToken))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$", hasSize(2)))
                        .andExpect(jsonPath("$[0].productId").exists())
                        .andExpect(jsonPath("$[0].productName").exists())
                        .andExpect(jsonPath("$[0].valid").isBoolean())
                        .andExpect(jsonPath("$[0].message").exists());
        }

        @Test
        void whenVerifyLedgerIntegrity_WithoutAuth_ThenUnauthorized() throws Exception {
                mockMvc.perform(get(BASE_URL + "/ledger-integrity"))
                        .andExpect(status().isUnauthorized());
        }

        @Test
        void whenVerifyLedgerIntegrity_WithUserRole_ThenForbidden() throws Exception {
                // Crear usuario con rol USER
                User regularUser = TestDataUtil.createUser("Regular User", "regularuser", "password", Role.USER);
                userRepository.saveAndFlush(regularUser);

                LoginRequestDTO loginRequest = new LoginRequestDTO(regularUser.getUser(), "password");
                String response = mockMvc
                                .perform(post("/api/auth/login")
                                                .contentType("application/json")
                                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                LoginResponseDTO loginResponse = objectMapper.readValue(response, LoginResponseDTO.class);
                String regularToken = loginResponse.getToken();

                mockMvc.perform(get(BASE_URL + "/ledger-integrity")
                                .header("Authorization", "Bearer " + regularToken))
                        .andExpect(status().isForbidden());
        }

        @Test
        void whenDownloadPdfWithIntegrity_ThenIncludesIntegrityHeaders() throws Exception {
                mockMvc.perform(get(BASE_URL + "/{id}/ledger/pdf", testProduct1.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                        .andExpect(status().isOk())
                        .andExpect(header().exists("X-Ledger-Integrity-Valid"))
                        .andExpect(header().exists("X-Ledger-Integrity-Message"))
                        .andExpect(header().string("Content-Type", "application/pdf"));
        }

        @Test
        void whenProductHasNoLedger_WithLedgerEndpoint_ThenReturnsEmptyList() throws Exception {
                // Limpiar ledgers
                stockLedgerRepository.deleteAll();

                mockMvc.perform(get(BASE_URL + "/with-ledger")
                                .header("Authorization", "Bearer " + jwtToken))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content", hasSize(0)))
                        .andExpect(jsonPath("$.totalElements").value(0));
        }
}
