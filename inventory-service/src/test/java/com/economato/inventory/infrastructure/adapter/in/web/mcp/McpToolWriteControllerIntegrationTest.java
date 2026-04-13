package com.economato.inventory.infrastructure.adapter.in.web.mcp;

import com.economato.inventory.application.dto.mcp.McpAdjustStockRequest;
import com.economato.inventory.application.dto.mcp.McpCreateOrderRequest;
import com.economato.inventory.application.dto.mcp.McpIncidentRequest;
import com.economato.inventory.application.dto.mcp.McpOrderItemRequest;
import com.economato.inventory.application.usecase.mcp.McpToolWriteService;
import com.economato.inventory.infrastructure.TestDataUtil;
import com.economato.inventory.infrastructure.adapter.in.web.BaseIntegrationTest;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpToolWriteControllerIntegrationTest extends BaseIntegrationTest {

    private static final String SERVICE_KEY = "test-service-key-for-integration-tests";

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private McpToolWriteService mcpToolWriteService;

    @BeforeEach
    void setUp() {
        clearDatabase();
        userRepository.saveAndFlush(TestDataUtil.createAdminUser());
    }

    @Test
    void createOrder_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/mcp/tools/create-order")
                        .header("X-Service-Key", SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpCreateOrderRequest(1, List.of(new McpOrderItemRequest(2, BigDecimal.ONE))))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createOrder_withAuth_delegatesToService() throws Exception {
        String token = loginAsAdmin();
        when(mcpToolWriteService.createOrder(any())).thenReturn(null);

        mockMvc.perform(post("/api/mcp/tools/create-order")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpCreateOrderRequest(1, List.of(new McpOrderItemRequest(2, BigDecimal.ONE))))))
                .andExpect(status().isOk());

        verify(mcpToolWriteService).createOrder(any());
    }

    @Test
    void adjustStock_withAuth_returnsPayload() throws Exception {
        String token = loginAsAdmin();
        when(mcpToolWriteService.adjustStock(any())).thenReturn(Map.of("status", "ok"));

        mockMvc.perform(post("/api/mcp/tools/adjust-stock")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpAdjustStockRequest(2, new BigDecimal("3.5"), "test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")));

        verify(mcpToolWriteService).adjustStock(any());
    }

    @Test
    void reportIncident_withAuth_returnsPayload() throws Exception {
        String token = loginAsAdmin();
        when(mcpToolWriteService.reportIncident(any())).thenReturn(Map.of("incidentId", 99, "status", "OPEN"));

        mockMvc.perform(post("/api/mcp/tools/report-incident")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpIncidentRequest("Falla", "Descripcion", "HIGH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId", is(99)));

        verify(mcpToolWriteService).reportIncident(any());
    }
}
