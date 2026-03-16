package com.economato.inventory.infrastructure.adapter.in.web.mcp;

import com.economato.inventory.infrastructure.adapter.in.web.BaseIntegrationTest;
import com.economato.inventory.application.dto.mcp.McpBulkRequest;
import com.economato.inventory.domain.model.Product;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.ProductRepository;
import com.economato.inventory.infrastructure.TestDataUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

class McpUtilityControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        clearDatabase();
        createDefaultAdmin();
    }

    @Test
    void getSystemContext_WhenAuthenticated_ShouldReturnOk() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/mcp/context")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProducts", is(0)));
    }

    @Test
    void getSystemContext_WhenNotAuthenticated_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/mcp/context"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unifiedSearch_WhenAuthenticated_ShouldReturnResults() throws Exception {
        String token = loginAsAdmin();
        Product product = TestDataUtil.createProduct("Cucumber", "VEGETABLE", "KG", new BigDecimal("1.0"), "CUC001", new BigDecimal("10.0"));
        productRepository.saveAndFlush(product);

        mockMvc.perform(get("/api/mcp/search")
                .header("Authorization", "Bearer " + token)
                .param("q", "Cucumber"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(1)))
                .andExpect(jsonPath("$.products[0].name", is("Cucumber")));
    }

    @Test
    void getProductsBulk_WhenAuthenticated_ShouldReturnProducts() throws Exception {
        String token = loginAsAdmin();
        Product product = TestDataUtil.createProduct("Tomato", "VEGETABLE", "KG", new BigDecimal("0.5"), "TOM001", new BigDecimal("20.0"));
        product = productRepository.saveAndFlush(product);

        McpBulkRequest request = new McpBulkRequest(Arrays.asList(product.getId()), null);

        mockMvc.perform(post("/api/mcp/bulk/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Tomato")));
    }

    @Test
    void getLowStockProducts_WhenAuthenticated_ShouldReturnFilteredList() throws Exception {
        String token = loginAsAdmin();
        // Product with stock < minimum
        Product p = TestDataUtil.createProduct("Low Stock Item", "OTHER", "UND", new BigDecimal("10.0"), "LS001", new BigDecimal("1.0"));
        p.setMinimumStock(new BigDecimal("5.0"));
        productRepository.saveAndFlush(p);

        mockMvc.perform(get("/api/mcp/products/low-stock")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Low Stock Item")));
    }
}
