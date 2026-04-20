package com.economato.inventory.infrastructure.adapter.in.web.mcp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.economato.inventory.application.dto.mcp.McpAlertDto;
import com.economato.inventory.application.dto.mcp.McpBatchDto;
import com.economato.inventory.application.dto.mcp.McpComponentDto;
import com.economato.inventory.application.dto.mcp.McpCrisisDto;
import com.economato.inventory.application.dto.mcp.McpCrisisProductDto;
import com.economato.inventory.application.dto.mcp.McpExpiringBatchDto;
import com.economato.inventory.application.dto.mcp.McpFeasibilityDto;
import com.economato.inventory.application.dto.mcp.McpLedgerEntryDto;
import com.economato.inventory.application.dto.mcp.McpPredictionDto;
import com.economato.inventory.application.dto.mcp.McpProductDeepDto;
import com.economato.inventory.application.dto.mcp.McpRecipeDeepDto;
import com.economato.inventory.application.dto.mcp.McpSlotDto;
import com.economato.inventory.application.dto.mcp.McpSupplierDeepDto;
import com.economato.inventory.application.dto.mcp.McpWeeklyPlanDeepDto;
import com.economato.inventory.application.usecase.mcp.McpToolReadService;
import com.economato.inventory.infrastructure.TestDataUtil;
import com.economato.inventory.infrastructure.adapter.in.web.BaseIntegrationTest;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;

class McpToolReadControllerIntegrationTest extends BaseIntegrationTest {

    private static final String SERVICE_KEY = "test-service-key-for-integration-tests";

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private McpToolReadService mcpToolReadService;

    @BeforeEach
        @SuppressWarnings("unused")
    void setUp() {
        clearDatabase();
        userRepository.saveAndFlush(TestDataUtil.createAdminUser());
    }

    @Test
    void getProductDeep_returns200() throws Exception {
        String token = loginAsAdmin();
        when(mcpToolReadService.getProductDeep(42)).thenReturn(productDeep());

        mockMvc.perform(get("/api/mcp/products/42/deep")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(42)))
                .andExpect(jsonPath("$.name", is("Tomate")))
                .andExpect(jsonPath("$.batches", hasSize(1)));
    }

    @Test
    void getRecipeDeep_returns200() throws Exception {
        String token = loginAsAdmin();
        when(mcpToolReadService.getRecipeDeep(15)).thenReturn(recipeDeep());

        mockMvc.perform(get("/api/mcp/recipes/15/deep")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(15)))
                .andExpect(jsonPath("$.components", hasSize(1)))
                .andExpect(jsonPath("$.allergens", hasSize(1)));
    }

    @Test
    void checkFeasibility_returns200() throws Exception {
        String token = loginAsAdmin();
        when(mcpToolReadService.checkFeasibility(eq(15), eq(new BigDecimal("30"))))
                .thenReturn(new McpFeasibilityDto(true, List.of()));

        mockMvc.perform(get("/api/mcp/recipes/15/feasibility")
                        .param("portions", "30")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feasible", is(true)));
    }

    @Test
    void getCurrentWeeklyPlanDeep_returns200() throws Exception {
        String token = loginAsAdmin();
        when(mcpToolReadService.getCurrentWeeklyPlanDeep()).thenReturn(new McpWeeklyPlanDeepDto(
                99L,
                "ACTIVE",
                LocalDate.of(2026, 4, 13),
                LocalDate.of(2026, 4, 19),
                List.of(new McpSlotDto(1L, 15, "Paella", new BigDecimal("10"), 1, LocalTime.of(12, 0), LocalTime.of(13, 0), "CONFIRMED")),
                Map.of(15, new BigDecimal("2.500"))
        ));

        mockMvc.perform(get("/api/mcp/weekly-plan/current/deep")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId", is(99)))
                .andExpect(jsonPath("$.slots", hasSize(1)));
    }

    @Test
    void getProductLedger_returns200() throws Exception {
        String token = loginAsAdmin();
        when(mcpToolReadService.getProductLedger(42, 20)).thenReturn(List.of(new McpLedgerEntryDto(
                7L,
                "SALIDA",
                new BigDecimal("-2.000"),
                new BigDecimal("18.000"),
                "consumption",
                LocalDateTime.of(2026, 4, 13, 10, 0),
                "Admin"
        )));

        mockMvc.perform(get("/api/mcp/products/42/ledger")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(7)))
                .andExpect(jsonPath("$[0].movementType", is("SALIDA")));
    }

    @Test
    void getSupplierDeep_returns200() throws Exception {
        String token = loginAsAdmin();
        when(mcpToolReadService.getSupplierDeep(3)).thenReturn(new McpSupplierDeepDto(
                3,
                "Proveedor Central",
                "555-123",
                "proveedor@test.local",
                List.of(new com.economato.inventory.application.dto.mcp.McpProductDto(42, "Tomate", "P42", new BigDecimal("10"), "kg", new BigDecimal("2.5"), new BigDecimal("1.000"))),
                2,
                true
        ));

        mockMvc.perform(get("/api/mcp/suppliers/3/deep")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Proveedor Central")))
                .andExpect(jsonPath("$.hasCrisis", is(true)));
    }

    @Test
    void getActiveCrises_returns200() throws Exception {
        String token = loginAsAdmin();
        when(mcpToolReadService.getActiveCrises()).thenReturn(List.of(new McpCrisisDto(
                1L,
                "CR-1",
                "Supplier issue",
                "Proveedor Central",
                "ACTIVE",
                LocalDateTime.of(2026, 4, 1, 0, 0),
                null,
                List.of(new McpCrisisProductDto(42, "Tomate", new BigDecimal("3")))
        )));

        mockMvc.perform(get("/api/mcp/crises/active")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].crisisCode", is("CR-1")));
    }

    @Test
    void getExpiringSoon_returns200() throws Exception {
        String token = loginAsAdmin();
        when(mcpToolReadService.getExpiringSoon(7)).thenReturn(List.of(new McpExpiringBatchDto(
                42,
                "Tomate",
                8L,
                LocalDate.of(2026, 4, 20),
                new BigDecimal("5.000"),
                6
        )));

        mockMvc.perform(get("/api/mcp/expiring-soon")
                        .param("days", "7")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productName", is("Tomate")));
    }

    @Test
    void getActiveAlerts_returns200() throws Exception {
        String token = loginAsAdmin();
        when(mcpToolReadService.getActiveAlerts()).thenReturn(List.of(new McpAlertDto(
                42,
                "Tomate",
                "HIGH",
                "UNCOVERED",
                new BigDecimal("10.000"),
                new BigDecimal("12.000"),
                new BigDecimal("0.000")
        )));

        mockMvc.perform(get("/api/mcp/alerts/active")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productName", is("Tomate")));
    }

    @Test
    void allReadEndpoints_withoutServiceKey_return403() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/mcp/products/42/deep")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/mcp/alerts/active")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private McpProductDeepDto productDeep() {
        return McpProductDeepDto.builder()
                .id(42)
                .name("Tomate")
                .code("P42")
                .stock(new BigDecimal("10.000"))
                .unit("kg")
                .price(new BigDecimal("2.500"))
                .supplierName("Proveedor Central")
                .alertLevel("LOW")
                .daysToNearestExpiry(4)
                .prediction(new McpPredictionDto(new BigDecimal("12.000"), LocalDateTime.of(2026, 4, 13, 9, 0)))
                .dailyForecast(List.of(new BigDecimal("1.1"), new BigDecimal("1.2")))
                .weeklyConsumption(List.of(new BigDecimal("3.3")))
                .batches(List.of(new McpBatchDto(1L, LocalDate.of(2026, 4, 20), new BigDecimal("5.000"), false, 7)))
                .build();
    }

    private McpRecipeDeepDto recipeDeep() {
        return McpRecipeDeepDto.builder()
                .id(15)
                .name("Paella")
                .code("R15")
                .cost(new BigDecimal("12.500"))
                .description("Desc")
                .preparation("Prep")
                .components(List.of(new McpComponentDto(42, "Tomate", new BigDecimal("1.000"), "kg", new BigDecimal("10.000"), true)))
                .allergens(List.of("gluten"))
                .costPerPortion(new BigDecimal("2.50"))
                .recentCookingCount(3)
                .build();
    }
}