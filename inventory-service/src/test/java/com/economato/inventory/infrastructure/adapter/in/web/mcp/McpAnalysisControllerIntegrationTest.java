package com.economato.inventory.infrastructure.adapter.in.web.mcp;

import com.economato.inventory.application.usecase.mcp.McpAnalysisService;
import com.economato.inventory.infrastructure.TestDataUtil;
import com.economato.inventory.infrastructure.adapter.in.web.BaseIntegrationTest;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpAnalysisControllerIntegrationTest extends BaseIntegrationTest {

    private static final String SERVICE_KEY = "test-service-key-for-integration-tests";

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private McpAnalysisService mcpAnalysisService;

    @BeforeEach
    void setUp() {
        clearDatabase();
        userRepository.saveAndFlush(TestDataUtil.createAdminUser());
    }

    @Test
    void reorderSuggestions_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/mcp/analysis/reorder-suggestions")
                .header("X-Service-Key", SERVICE_KEY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reorderSuggestions_withAuth_returnsOk() throws Exception {
        String token = loginAsAdmin();
        when(mcpAnalysisService.getReorderSuggestions()).thenReturn(List.of());

        mockMvc.perform(get("/api/mcp/analysis/reorder-suggestions")
                .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        verify(mcpAnalysisService).getReorderSuggestions();
    }

    @Test
    void menuOptimizer_withAuth_forwardsParams() throws Exception {
        String token = loginAsAdmin();
        when(mcpAnalysisService.getMenuOptimizer(any(), any())).thenReturn(null);

        mockMvc.perform(get("/api/mcp/analysis/menu-optimizer")
                .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token)
                        .param("budget", "500")
                        .param("exclude", "gluten", "lactosa"))
                .andExpect(status().isOk());

        verify(mcpAnalysisService).getMenuOptimizer(new BigDecimal("500"), List.of("gluten", "lactosa"));
    }

    @Test
    void costBreakdown_withAuth_forwardsDates() throws Exception {
        String token = loginAsAdmin();
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        when(mcpAnalysisService.getCostBreakdown(from, to)).thenReturn(null);

        mockMvc.perform(get("/api/mcp/analysis/cost-breakdown")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31"))
                .andExpect(status().isOk());

        verify(mcpAnalysisService).getCostBreakdown(from, to);
    }
}
