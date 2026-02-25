package com.economato.inventory.controller;

import com.economato.inventory.dto.request.LoginRequestDTO;
import com.economato.inventory.dto.request.ProductRequestDTO;
import com.economato.inventory.dto.response.LoginResponseDTO;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.UserRepository;
import com.economato.inventory.util.TestDataUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductControllerEdgeCasesTest extends BaseIntegrationTest {

        private static final String BASE_URL = "/api/products";
        private static final String AUTH_URL = "/api/auth/login";

        @Autowired
        private UserRepository userRepository;

        private String jwtToken;
        private User testAdmin;

        @BeforeEach
        void setUp() throws Exception {
                clearDatabase();

                testAdmin = TestDataUtil.createAdminUser();
                userRepository.saveAndFlush(testAdmin);

                LoginRequestDTO loginRequest = new LoginRequestDTO();
                loginRequest.setName(testAdmin.getName());
                loginRequest.setPassword("admin123");

                String response = mockMvc.perform(post(AUTH_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                LoginResponseDTO loginResponse = objectMapper.readValue(response, LoginResponseDTO.class);
                jwtToken = loginResponse.getToken();
        }

        @Test
        void createProduct_WithNegativeStock_ShouldReturnBadRequest() throws Exception {

                ProductRequestDTO productRequest = new ProductRequestDTO();
                productRequest.setName("Test Product");
                productRequest.setType("INGREDIENT");
                productRequest.setUnit("KG");
                productRequest.setCurrentStock(new BigDecimal("-5.0"));
                productRequest.setUnitPrice(new BigDecimal("10.0"));
                productRequest.setProductCode("TEST001");

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void createProduct_WithNegativePrice_ShouldReturnBadRequest() throws Exception {

                ProductRequestDTO productRequest = new ProductRequestDTO();
                productRequest.setName("Test Product");
                productRequest.setType("INGREDIENT");
                productRequest.setUnit("KG");
                productRequest.setCurrentStock(new BigDecimal("5.0"));
                productRequest.setUnitPrice(new BigDecimal("-10.0"));
                productRequest.setProductCode("TEST001");

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void createProduct_WithEmptyName_ShouldReturnBadRequest() throws Exception {

                ProductRequestDTO productRequest = new ProductRequestDTO();
                productRequest.setName("");
                productRequest.setType("INGREDIENT");
                productRequest.setUnit("KG");
                productRequest.setCurrentStock(new BigDecimal("5.0"));
                productRequest.setUnitPrice(new BigDecimal("10.0"));
                productRequest.setProductCode("TEST001");

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void createProduct_WithInvalidUnit_ShouldReturnBadRequest() throws Exception {

                ProductRequestDTO productRequest = new ProductRequestDTO();
                productRequest.setName("Test Product");
                productRequest.setType("INGREDIENT");
                productRequest.setUnit("INVALID");
                productRequest.setCurrentStock(new BigDecimal("5.0"));
                productRequest.setUnitPrice(new BigDecimal("10.0"));
                productRequest.setProductCode("TEST001");

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void createProduct_WithDuplicateName_ShouldReturnBadRequest() throws Exception {

                ProductRequestDTO productRequest = new ProductRequestDTO();
                productRequest.setName("Duplicate Product");
                productRequest.setType("INGREDIENT");
                productRequest.setUnit("KG");
                productRequest.setCurrentStock(new BigDecimal("5.0"));
                productRequest.setUnitPrice(new BigDecimal("10.0"));
                productRequest.setProductCode("TEST001");
                productRequest.setMinimumStock(BigDecimal.ZERO);

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isCreated());

                productRequest.setProductCode("TEST002");
                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void getProduct_WithNonExistentId_ShouldReturnNotFound() throws Exception {

                mockMvc.perform(get(BASE_URL + "/99999")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNotFound());
        }

        @Test
        void updateProduct_WithNonExistentId_ShouldReturnNotFound() throws Exception {

                ProductRequestDTO productRequest = new ProductRequestDTO();
                productRequest.setName("Updated Product");
                productRequest.setType("INGREDIENT");
                productRequest.setUnit("KG");
                productRequest.setCurrentStock(new BigDecimal("5.0"));
                productRequest.setUnitPrice(new BigDecimal("10.0"));
                productRequest.setProductCode("TEST001");
                productRequest.setMinimumStock(BigDecimal.ZERO);

                mockMvc.perform(put(BASE_URL + "/99999")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isNotFound());
        }

        @Test
        void deleteProduct_WithNonExistentId_ShouldReturn404() throws Exception {

                mockMvc.perform(delete(BASE_URL + "/99999")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNotFound());
        }

        @Test
        void getAllProducts_WithPagination_ShouldReturnCorrectPage() throws Exception {

                for (int i = 1; i <= 15; i++) {
                        ProductRequestDTO productRequest = new ProductRequestDTO();
                        productRequest.setName("Product " + i);
                        productRequest.setType("INGREDIENT");
                        productRequest.setUnit("KG");
                        productRequest.setCurrentStock(new BigDecimal("5.0"));
                        productRequest.setUnitPrice(new BigDecimal("10.0"));
                        productRequest.setProductCode("CODE" + i);
                        productRequest.setMinimumStock(BigDecimal.ZERO);

                        mockMvc.perform(post(BASE_URL)
                                        .header("Authorization", "Bearer " + jwtToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(asJsonString(productRequest)))
                                        .andExpect(status().isCreated());
                }

                mockMvc.perform(get(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk());

                mockMvc.perform(get(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .param("page", "1")
                                .param("size", "10"))
                                .andExpect(status().isOk());
        }

        @Test
        void searchProducts_WithFilters_ShouldReturnFilteredResults() throws Exception {

                ProductRequestDTO ingredientProduct = new ProductRequestDTO();
                ingredientProduct.setName("Flour");
                ingredientProduct.setType("INGREDIENT");
                ingredientProduct.setUnit("KG");
                ingredientProduct.setCurrentStock(new BigDecimal("10.0"));
                ingredientProduct.setUnitPrice(new BigDecimal("5.0"));
                ingredientProduct.setProductCode("FLOUR001");
                ingredientProduct.setMinimumStock(BigDecimal.ZERO);

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(ingredientProduct)))
                                .andExpect(status().isCreated());

                mockMvc.perform(get(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk());
        }

        @Test
        void createProduct_WithoutAuthentication_ShouldReturnUnauthorized() throws Exception {

                ProductRequestDTO productRequest = new ProductRequestDTO();
                productRequest.setName("Test Product");
                productRequest.setType("INGREDIENT");
                productRequest.setUnit("KG");
                productRequest.setCurrentStock(new BigDecimal("5.0"));
                productRequest.setUnitPrice(new BigDecimal("10.0"));
                productRequest.setProductCode("TEST001");

                mockMvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void createProduct_WithInvalidToken_ShouldReturnUnauthorized() throws Exception {

                ProductRequestDTO productRequest = new ProductRequestDTO();
                productRequest.setName("Test Product");
                productRequest.setType("INGREDIENT");
                productRequest.setUnit("KG");
                productRequest.setCurrentStock(new BigDecimal("5.0"));
                productRequest.setUnitPrice(new BigDecimal("10.0"));
                productRequest.setProductCode("TEST001");

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer invalid_token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isUnauthorized());
        }
}
