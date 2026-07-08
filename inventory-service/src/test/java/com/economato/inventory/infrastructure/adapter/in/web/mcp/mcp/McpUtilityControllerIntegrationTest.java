package com.economato.inventory.infrastructure.adapter.in.web.mcp.mcp;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;

import com.economato.inventory.infrastructure.adapter.in.web.shared.BaseIntegrationTest;
import com.economato.inventory.application.dto.mcp.mcp.McpBulkRequest;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.shared.TestDataUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.Arrays;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.economato.inventory.infrastructure.adapter.out.messaging.shared.kafka.producer.AuditEventProducer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

class McpUtilityControllerIntegrationTest extends BaseIntegrationTest {

    private static final String SERVICE_KEY = "test-service-key-for-integration-tests";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        clearDatabase();
        userRepository.saveAndFlush(TestDataUtil.createAdminUser());
    }

    @Test
    void getSystemContext_WhenAuthenticated_ShouldReturnOk() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/mcp/context")
            .header("X-Service-Key", SERVICE_KEY)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProducts", is(0)));
    }

    @Test
    void getSystemContext_WhenNotAuthenticated_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/mcp/context")
            .header("X-Service-Key", SERVICE_KEY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unifiedSearch_WhenAuthenticated_ShouldReturnResults() throws Exception {
        String token = loginAsAdmin();
        Product product = TestDataUtil.createProduct("Cucumber", "KG", new BigDecimal("1.0"), "CUC001", new BigDecimal("10.0"));
        productRepository.saveAndFlush(product);

        mockMvc.perform(get("/api/mcp/search")
            .header("X-Service-Key", SERVICE_KEY)
                .header("Authorization", "Bearer " + token)
                .param("q", "Cucumber"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(1)))
                .andExpect(jsonPath("$.products[0].name", is("Cucumber")));
    }

    @Test
    void getProductsBulk_WhenAuthenticated_ShouldReturnProducts() throws Exception {
        String token = loginAsAdmin();
        Product product = TestDataUtil.createProduct("Tomato", "KG", new BigDecimal("0.5"), "TOM001", new BigDecimal("20.0"));
        product = productRepository.saveAndFlush(product);

        McpBulkRequest request = new McpBulkRequest(Arrays.asList(product.getId()), null);

        mockMvc.perform(post("/api/mcp/bulk/products")
            .header("X-Service-Key", SERVICE_KEY)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Tomato")));
    }

}