package com.economato.inventory.infrastructure.adapter.in.web.mcp;

import com.economato.inventory.application.usecase.mcp.McpToolReadService;
import com.economato.inventory.infrastructure.TestDataUtil;
import com.economato.inventory.infrastructure.adapter.in.web.BaseIntegrationTest;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpSecurityIntegrationTest extends BaseIntegrationTest {

    private static final String SERVICE_KEY = "test-service-key-for-integration-tests";

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private McpToolReadService mcpToolReadService;

    @BeforeEach
    void setUp() {
        clearDatabase();
        userRepository.saveAndFlush(TestDataUtil.createAdminUser());
        when(mcpToolReadService.getActiveAlerts()).thenReturn(java.util.List.of());
    }

    @Test
    void mcpEndpoint_withServiceKeyAndJwt_returns200() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/mcp/alerts/active")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void mcpEndpoint_withoutServiceKey_returns403() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/mcp/alerts/active")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void mcpEndpoint_withWrongServiceKey_returns403() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/mcp/alerts/active")
                        .header("X-Service-Key", "wrong-key")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void mcpEndpoint_withServiceKeyButNoJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/mcp/alerts/active")
                        .header("X-Service-Key", SERVICE_KEY))
                .andExpect(status().isUnauthorized());
    }
}