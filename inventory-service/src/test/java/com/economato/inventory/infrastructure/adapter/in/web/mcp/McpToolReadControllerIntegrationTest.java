package com.economato.inventory.infrastructure.adapter.in.web.mcp;

import com.economato.inventory.application.usecase.mcp.McpToolReadService;
import com.economato.inventory.infrastructure.TestDataUtil;
import com.economato.inventory.infrastructure.adapter.in.web.BaseIntegrationTest;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpToolReadControllerIntegrationTest extends BaseIntegrationTest {

    private static final String SERVICE_KEY = "test-service-key-for-integration-tests";

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private McpToolReadService mcpToolReadService;

    @BeforeEach
    void setUp() {
        clearDatabase();
        userRepository.saveAndFlush(TestDataUtil.createAdminUser());
    }

    @Test
    void getProductDeep_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/mcp/products/1/deep")
                .header("X-Service-Key", SERVICE_KEY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProductLedger_withAuth_delegatesLimit() throws Exception {
        String token = loginAsAdmin();
        when(mcpToolReadService.getProductLedger(1, 15)).thenReturn(List.of());

        mockMvc.perform(get("/api/mcp/products/1/ledger")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token)
                        .param("limit", "15"))
                .andExpect(status().isOk());

        verify(mcpToolReadService).getProductLedger(1, 15);
    }

    @Test
    void getExpiringSoon_withAuth_delegatesDays() throws Exception {
        String token = loginAsAdmin();
        when(mcpToolReadService.getExpiringSoon(9)).thenReturn(List.of());

        mockMvc.perform(get("/api/mcp/expiring-soon")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token)
                        .param("days", "9"))
                .andExpect(status().isOk());

        verify(mcpToolReadService).getExpiringSoon(9);
    }

    @Test
    void getActiveAlerts_withAuth_returnsOk() throws Exception {
        String token = loginAsAdmin();
        when(mcpToolReadService.getActiveAlerts()).thenReturn(List.of());

        mockMvc.perform(get("/api/mcp/alerts/active")
                .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        verify(mcpToolReadService).getActiveAlerts();
    }
}
