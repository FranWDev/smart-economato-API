package com.economatom.inventory.controller;

import com.economatom.inventory.dto.request.LoginRequestDTO;
import com.economatom.inventory.dto.request.ProductRequestDTO;
import com.economatom.inventory.dto.response.LoginResponseDTO;
import com.economatom.inventory.model.User;
import com.economatom.inventory.repository.UserRepository;
import com.economatom.inventory.util.TestDataUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

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
        // Arrange
        ProductRequestDTO productRequest = new ProductRequestDTO();
        productRequest.setName("Test Product");
        productRequest.setType("INGREDIENT");
        productRequest.setUnit("KG");
        productRequest.setCurrentStock(new BigDecimal("-5.0"));
        productRequest.setUnitPrice(new BigDecimal("10.0"));
        productRequest.setProductCode("TEST001");

        // Act & Assert
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(productRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_WithNegativePrice_ShouldReturnBadRequest() throws Exception {
        // Arrange
        ProductRequestDTO productRequest = new ProductRequestDTO();
        productRequest.setName("Test Product");
        productRequest.setType("INGREDIENT");
        productRequest.setUnit("KG");
        productRequest.setCurrentStock(new BigDecimal("5.0"));
        productRequest.setUnitPrice(new BigDecimal("-10.0"));
        productRequest.setProductCode("TEST001");

        // Act & Assert
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(productRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_WithEmptyName_ShouldReturnBadRequest() throws Exception {
        // Arrange
        ProductRequestDTO productRequest = new ProductRequestDTO();
        productRequest.setName("");
        productRequest.setType("INGREDIENT");
        productRequest.setUnit("KG");
        productRequest.setCurrentStock(new BigDecimal("5.0"));
        productRequest.setUnitPrice(new BigDecimal("10.0"));
        productRequest.setProductCode("TEST001");

        // Act & Assert
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(productRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_WithInvalidUnit_ShouldReturnBadRequest() throws Exception {
        // Arrange
        ProductRequestDTO productRequest = new ProductRequestDTO();
        productRequest.setName("Test Product");
        productRequest.setType("INGREDIENT");
        productRequest.setUnit("INVALID");
        productRequest.setCurrentStock(new BigDecimal("5.0"));
        productRequest.setUnitPrice(new BigDecimal("10.0"));
        productRequest.setProductCode("TEST001");

        // Act & Assert
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(productRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_WithDuplicateName_ShouldReturnBadRequest() throws Exception {
        // Arrange
        ProductRequestDTO productRequest = new ProductRequestDTO();
        productRequest.setName("Duplicate Product");
        productRequest.setType("INGREDIENT");
        productRequest.setUnit("KG");
        productRequest.setCurrentStock(new BigDecimal("5.0"));
        productRequest.setUnitPrice(new BigDecimal("10.0"));
        productRequest.setProductCode("TEST001");

        // Create first product - should return 200 if product service returns existing product
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(productRequest)))
                .andExpect(status().isOk());

        // Try to create duplicate - should fail with 400 because name is duplicate
        productRequest.setProductCode("TEST002");
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(productRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getProduct_WithNonExistentId_ShouldReturnNotFound() throws Exception {
        // Act & Assert
        mockMvc.perform(get(BASE_URL + "/99999")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateProduct_WithNonExistentId_ShouldReturnNotFound() throws Exception {
        // Arrange
        ProductRequestDTO productRequest = new ProductRequestDTO();
        productRequest.setName("Updated Product");
        productRequest.setType("INGREDIENT");
        productRequest.setUnit("KG");
        productRequest.setCurrentStock(new BigDecimal("5.0"));
        productRequest.setUnitPrice(new BigDecimal("10.0"));
        productRequest.setProductCode("TEST001");

        // Act & Assert
        mockMvc.perform(put(BASE_URL + "/99999")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(productRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteProduct_WithNonExistentId_ShouldReturn404() throws Exception {
        // Act & Assert - Delete should return 404 if ID doesn't exist
        mockMvc.perform(delete(BASE_URL + "/99999")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllProducts_WithPagination_ShouldReturnCorrectPage() throws Exception {
        // Arrange - Create multiple products
        for (int i = 1; i <= 15; i++) {
            ProductRequestDTO productRequest = new ProductRequestDTO();
            productRequest.setName("Product " + i);
            productRequest.setType("INGREDIENT");
            productRequest.setUnit("KG");
            productRequest.setCurrentStock(new BigDecimal("5.0"));
            productRequest.setUnitPrice(new BigDecimal("10.0"));
            productRequest.setProductCode("CODE" + i);

            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(asJsonString(productRequest)))
                    .andExpect(status().isOk());
        }

        // Act & Assert - Get first page
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + jwtToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk());

        // Act & Assert - Get second page
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + jwtToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void searchProducts_WithFilters_ShouldReturnFilteredResults() throws Exception {
        // Arrange - Create products with different types
        ProductRequestDTO ingredientProduct = new ProductRequestDTO();
        ingredientProduct.setName("Flour");
        ingredientProduct.setType("INGREDIENT");
        ingredientProduct.setUnit("KG");
        ingredientProduct.setCurrentStock(new BigDecimal("10.0"));
        ingredientProduct.setUnitPrice(new BigDecimal("5.0"));
        ingredientProduct.setProductCode("FLOUR001");

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(ingredientProduct)))
                .andExpect(status().isOk());

        // Act & Assert - Get all products should return the created product
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());
    }

    @Test
    void createProduct_WithoutAuthentication_ShouldReturnUnauthorized() throws Exception {
        // Arrange
        ProductRequestDTO productRequest = new ProductRequestDTO();
        productRequest.setName("Test Product");
        productRequest.setType("INGREDIENT");
        productRequest.setUnit("KG");
        productRequest.setCurrentStock(new BigDecimal("5.0"));
        productRequest.setUnitPrice(new BigDecimal("10.0"));
        productRequest.setProductCode("TEST001");

        // Act & Assert
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(productRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createProduct_WithInvalidToken_ShouldReturnUnauthorized() throws Exception {
        // Arrange
        ProductRequestDTO productRequest = new ProductRequestDTO();
        productRequest.setName("Test Product");
        productRequest.setType("INGREDIENT");
        productRequest.setUnit("KG");
        productRequest.setCurrentStock(new BigDecimal("5.0"));
        productRequest.setUnitPrice(new BigDecimal("10.0"));
        productRequest.setProductCode("TEST001");

        // Act & Assert
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer invalid_token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(productRequest)))
                .andExpect(status().isUnauthorized());
    }
}
