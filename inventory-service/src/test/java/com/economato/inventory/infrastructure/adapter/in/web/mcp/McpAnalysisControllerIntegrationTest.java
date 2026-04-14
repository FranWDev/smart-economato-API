package com.economato.inventory.infrastructure.adapter.in.web.mcp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.economato.inventory.application.dto.mcp.McpCostBreakdownDto;
import com.economato.inventory.application.dto.mcp.McpMenuDayDto;
import com.economato.inventory.application.dto.mcp.McpMenuRecipeDto;
import com.economato.inventory.application.dto.mcp.McpMenuSuggestionDto;
import com.economato.inventory.application.dto.mcp.McpReorderSuggestionDto;
import com.economato.inventory.application.dto.mcp.McpStockHealthDto;
import com.economato.inventory.application.dto.mcp.McpWasteRecipeSuggestionDto;
import com.economato.inventory.application.dto.mcp.McpWasteRiskDto;
import com.economato.inventory.application.usecase.mcp.McpAnalysisService;
import com.economato.inventory.infrastructure.TestDataUtil;
import com.economato.inventory.infrastructure.adapter.in.web.BaseIntegrationTest;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;

class McpAnalysisControllerIntegrationTest extends BaseIntegrationTest {

    private static final String SERVICE_KEY = "test-service-key-for-integration-tests";

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private McpAnalysisService mcpAnalysisService;

    @BeforeEach
        @SuppressWarnings("unused")
    void setUp() {
        clearDatabase();
        userRepository.saveAndFlush(TestDataUtil.createAdminUser());
    }

    @Test
    void reorderSuggestions_returns200() throws Exception {
        String token = loginAsAdmin();
        when(mcpAnalysisService.getReorderSuggestions()).thenReturn(List.of(new McpReorderSuggestionDto(
                42,
                "Tomate",
                new BigDecimal("10.000"),
                new BigDecimal("30.000"),
                new BigDecimal("5.000"),
                new BigDecimal("18.000"),
                "Proveedor Central",
                "HIGH"
        )));

        mockMvc.perform(get("/api/mcp/analysis/reorder-suggestions")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId", is(42)));
    }

    @Test
    void wasteRisk_returns200() throws Exception {
        String token = loginAsAdmin();
        when(mcpAnalysisService.getWasteRisk()).thenReturn(List.of(new McpWasteRiskDto(
                8L,
                42,
                "Tomate",
                LocalDate.of(2026, 4, 20),
                6,
                new BigDecimal("5.000"),
                List.of(new McpWasteRecipeSuggestionDto(15, "Paella", new BigDecimal("3.000"), true))
        )));

        mockMvc.perform(get("/api/mcp/analysis/waste-risk")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productName", is("Tomate")))
                .andExpect(jsonPath("$[0].recipeSuggestions", hasSize(1)));
    }

    @Test
    void menuOptimizer_returns200() throws Exception {
        String token = loginAsAdmin();
        when(mcpAnalysisService.getMenuOptimizer(new BigDecimal("100"), List.of("gluten", "lactosa"))).thenReturn(new McpMenuSuggestionDto(
                List.of(new McpMenuDayDto(1, List.of(new McpMenuRecipeDto(15, "Paella", new BigDecimal("2.50"), List.of("lactosa"))))),
                new BigDecimal("25.00")
        ));

        mockMvc.perform(get("/api/mcp/analysis/menu-optimizer")
                        .param("budget", "100")
                        .param("exclude", "gluten", "lactosa")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days", hasSize(1)))
                .andExpect(jsonPath("$.totalEstimatedCost", is(25.0)));
    }

    @Test
    void costBreakdown_returns200() throws Exception {
        String token = loginAsAdmin();
        when(mcpAnalysisService.getCostBreakdown(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 13))).thenReturn(new McpCostBreakdownDto(
                new BigDecimal("123.45"),
                Map.of("Paella", new BigDecimal("50.00")),
                Map.of("Tomate", new BigDecimal("20.00")),
                new BigDecimal("9.50")
        ));

        mockMvc.perform(get("/api/mcp/analysis/cost-breakdown")
                        .param("from", "2026-04-01")
                        .param("to", "2026-04-13")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost", is(123.45)));
    }

    @Test
    void stockHealthScore_returns200() throws Exception {
        String token = loginAsAdmin();
        when(mcpAnalysisService.getStockHealthScore()).thenReturn(new McpStockHealthDto(55, 1, 2, 2, 3, 1, 1));

        mockMvc.perform(get("/api/mcp/analysis/stock-health-score")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score", is(55)));
    }

    @Test
    void allAnalysisEndpoints_withoutServiceKey_return403() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/mcp/analysis/reorder-suggestions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/mcp/analysis/stock-health-score")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}