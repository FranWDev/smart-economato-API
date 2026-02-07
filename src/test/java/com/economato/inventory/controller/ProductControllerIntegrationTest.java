package com.economato.inventory.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.economato.inventory.dto.request.LoginRequestDTO;
import com.economato.inventory.dto.request.ProductRequestDTO;
import com.economato.inventory.dto.response.LoginResponseDTO;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.UserRepository;
import com.economato.inventory.util.TestDataUtil;

class ProductControllerIntegrationTest extends BaseIntegrationTest {

        private static final String BASE_URL = "/api/products";
        private static final String AUTH_URL = "/api/auth/login";

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        private Product testProduct;
        private String jwtToken;

        @BeforeEach
        void setUp() throws Exception {
                clearDatabase();

                User adminUser = TestDataUtil.createAdminUser();
                adminUser.setPassword(passwordEncoder.encode("admin123"));
                userRepository.saveAndFlush(adminUser);

                LoginRequestDTO loginRequest = new LoginRequestDTO();
                loginRequest.setName(adminUser.getName());
                loginRequest.setPassword("admin123");

                String response = mockMvc.perform(post(AUTH_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                LoginResponseDTO loginResponse = objectMapper.readValue(response, LoginResponseDTO.class);
                jwtToken = loginResponse.getToken();

                testProduct = TestDataUtil.createFlour();
                testProduct = productRepository.saveAndFlush(testProduct);
        }

        @Test
        void whenGetAllProducts_thenReturnsPaginatedProducts() throws Exception {
                mockMvc.perform(get(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", notNullValue()));
        }

        @Test
        void whenCreateValidProduct_thenReturnsCreatedProduct() throws Exception {
                ProductRequestDTO productRequest = TestDataUtil.createProductRequestDTO();

                mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is(productRequest.getName())))
                                .andExpect(jsonPath("$.type", is(productRequest.getType())))
                                .andExpect(jsonPath("$.unit", is(productRequest.getUnit())))
                                .andExpect(jsonPath("$.unitPrice", is(productRequest.getUnitPrice().doubleValue())))
                                .andExpect(jsonPath("$.productCode", is(productRequest.getProductCode())))
                                .andExpect(jsonPath("$.currentStock",
                                                is(productRequest.getCurrentStock().doubleValue())));
        }

        @Test
        void whenGetProductById_thenReturnsProduct() throws Exception {

                ProductRequestDTO productRequest = TestDataUtil.createProductRequestDTO();

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                Integer productId = objectMapper.readTree(response).get("id").asInt();

                mockMvc.perform(get(BASE_URL + "/{id}", productId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is(productRequest.getName())))
                                .andExpect(jsonPath("$.type", is(productRequest.getType())))
                                .andExpect(jsonPath("$.unit", is(productRequest.getUnit())))
                                .andExpect(jsonPath("$.unitPrice", is(productRequest.getUnitPrice().doubleValue())))
                                .andExpect(jsonPath("$.productCode", is(productRequest.getProductCode())))
                                .andExpect(jsonPath("$.currentStock",
                                                is(productRequest.getCurrentStock().doubleValue())));
        }

        @Test
        void whenUpdateProduct_thenReturnsUpdatedProduct() throws Exception {

                ProductRequestDTO productRequest = TestDataUtil.createProductRequestDTO();

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                Integer productId = objectMapper.readTree(response).get("id").asInt();

                productRequest.setName(productRequest.getName() + " Actualizado");
                productRequest.setUnitPrice(productRequest.getUnitPrice().add(new BigDecimal("1.00")));
                productRequest.setCurrentStock(productRequest.getCurrentStock().subtract(new BigDecimal("10.0")));

                mockMvc.perform(put(BASE_URL + "/{id}", productId)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is(productRequest.getName())))
                                .andExpect(jsonPath("$.unitPrice", is(productRequest.getUnitPrice().doubleValue())))
                                .andExpect(jsonPath("$.currentStock",
                                                is(productRequest.getCurrentStock().doubleValue())));
        }

        @Test
        void whenDeleteProduct_thenReturnsNoContent() throws Exception {

                ProductRequestDTO productRequest = TestDataUtil.createProductRequestDTO();

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                Integer productId = objectMapper.readTree(response).get("id").asInt();

                mockMvc.perform(delete(BASE_URL + "/{id}", productId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNoContent());

                mockMvc.perform(get(BASE_URL + "/{id}", productId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNotFound());
        }

        @Test
        void whenGetProductByCodebar_thenReturnsProduct() throws Exception {
                mockMvc.perform(get(BASE_URL + "/codebar/{codebar}", testProduct.getProductCode())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.productCode", is(testProduct.getProductCode())))
                                .andExpect(jsonPath("$.name", is(testProduct.getName())));
        }

        @Test
        void whenGetProductByInvalidCodebar_thenReturnsNotFound() throws Exception {
                mockMvc.perform(get(BASE_URL + "/codebar/{codebar}", "INVALID-CODE")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNotFound());
        }

        @Test
        void whenUpdateStockManually_WithStockChange_thenRegistersInStockLedger() throws Exception {

                ProductRequestDTO productRequest = TestDataUtil.createProductRequestDTO();
                productRequest.setCurrentStock(new BigDecimal("100.0"));

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                Integer productId = objectMapper.readTree(response).get("id").asInt();

                productRequest.setCurrentStock(new BigDecimal("150.0"));

                mockMvc.perform(put(BASE_URL + "/{id}/stock-manual", productId)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.currentStock", is(150.0)));

                mockMvc.perform(get(BASE_URL + "/{id}", productId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.currentStock", is(150.0)));
        }

        @Test
        void whenUpdateStockManually_WithoutStockChange_thenDoesNotRegister() throws Exception {

                ProductRequestDTO productRequest = TestDataUtil.createProductRequestDTO();
                productRequest.setCurrentStock(new BigDecimal("100.0"));

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                Integer productId = objectMapper.readTree(response).get("id").asInt();

                productRequest.setName(productRequest.getName() + " Actualizado");
                productRequest.setUnitPrice(productRequest.getUnitPrice().add(new BigDecimal("0.50")));

                mockMvc.perform(put(BASE_URL + "/{id}/stock-manual", productId)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is(productRequest.getName())))
                                .andExpect(jsonPath("$.unitPrice", is(productRequest.getUnitPrice().doubleValue())))
                                .andExpect(jsonPath("$.currentStock", is(100.0)));
        }

        @Test
        void whenUpdateStockManually_DecreasesStock_thenRegistersInStockLedger() throws Exception {

                ProductRequestDTO productRequest = TestDataUtil.createProductRequestDTO();
                productRequest.setCurrentStock(new BigDecimal("200.0"));

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                Integer productId = objectMapper.readTree(response).get("id").asInt();

                productRequest.setCurrentStock(new BigDecimal("150.0"));

                mockMvc.perform(put(BASE_URL + "/{id}/stock-manual", productId)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.currentStock", is(150.0)));

                mockMvc.perform(get(BASE_URL + "/{id}", productId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.currentStock", is(150.0)));
        }

        @Test
        void whenUpdateStockManually_WithInvalidData_thenReturnsBadRequest() throws Exception {

                ProductRequestDTO productRequest = TestDataUtil.createProductRequestDTO();

                String response = mockMvc.perform(post(BASE_URL)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(productRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                Integer productId = objectMapper.readTree(response).get("id").asInt();

                ProductRequestDTO invalidRequest = new ProductRequestDTO();
                invalidRequest.setName("");
                invalidRequest.setType(productRequest.getType());
                invalidRequest.setUnit(productRequest.getUnit());
                invalidRequest.setUnitPrice(productRequest.getUnitPrice());
                invalidRequest.setProductCode(productRequest.getProductCode());
                invalidRequest.setCurrentStock(productRequest.getCurrentStock());

                mockMvc.perform(put(BASE_URL + "/{id}/stock-manual", productId)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(asJsonString(invalidRequest)))
                                .andExpect(status().isBadRequest());
        }
}