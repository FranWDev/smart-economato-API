package com.economato.inventory.infrastructure.adapter.in.web.mcp.mcp;
import com.economato.inventory.domain.model.product.Supplier;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.economato.inventory.application.dto.stock.mcp.McpAdjustStockRequest;
import com.economato.inventory.application.dto.recipe.mcp.McpCookRecipeRequest;
import com.economato.inventory.application.dto.order.mcp.McpCreateOrderRequest;
import com.economato.inventory.application.dto.incident.mcp.McpIncidentRequest;
import com.economato.inventory.application.dto.order.mcp.McpOrderDto;
import com.economato.inventory.application.dto.order.mcp.McpOrderItemRequest;
import com.economato.inventory.application.dto.weeklyplan.mcp.McpPlanSlotRequest;
import com.economato.inventory.application.dto.mcp.mcp.McpQuarantineRequest;
import com.economato.inventory.application.dto.recipe.mcp.McpRecipeDto;
import com.economato.inventory.application.dto.weeklyplan.mcp.McpSlotDto;
import com.economato.inventory.application.usecase.mcp.mcp.McpToolWriteService;
import com.economato.inventory.infrastructure.shared.TestDataUtil;
import com.economato.inventory.infrastructure.adapter.in.web.shared.BaseIntegrationTest;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;

class McpToolWriteControllerIntegrationTest extends BaseIntegrationTest {

    private static final String SERVICE_KEY = "test-service-key-for-integration-tests";

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private McpToolWriteService mcpToolWriteService;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        clearDatabase();
        userRepository.saveAndFlush(TestDataUtil.createAdminUser());
    }

    @Test
    void createOrder_returns200() throws Exception {
        String token = loginAsAdmin();
        when(mcpToolWriteService.createOrder(any())).thenReturn(new McpOrderDto(11, "CREATED", new BigDecimal("25.50"), 1, "Supplier A", "2026-04-13"));

        mockMvc.perform(post("/api/mcp/tools/create-order")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpCreateOrderRequest(10, List.of(new McpOrderItemRequest(3, new BigDecimal("2.000")))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(11)))
                .andExpect(jsonPath("$.status", is("CREATED")));

        verify(mcpToolWriteService).createOrder(any());
    }

    @Test
    void cookRecipe_returns200() throws Exception {
        String token = loginAsAdmin();
        when(mcpToolWriteService.cookRecipe(any())).thenReturn(new McpRecipeDto(15, "Paella", "R15", new BigDecimal("12.500"), 2, "Desc", "Prep"));

        mockMvc.perform(post("/api/mcp/tools/cook-recipe")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpCookRecipeRequest(15, new BigDecimal("2")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(15)));
    }

    @Test
    void adjustStock_returns200() throws Exception {
        String token = loginAsAdmin();
        when(mcpToolWriteService.adjustStock(any())).thenReturn(Map.of("productId", 4, "newStock", new BigDecimal("17.000"), "movementType", "MODIFICACION", "transactionId", 99L));

        mockMvc.perform(post("/api/mcp/tools/adjust-stock")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpAdjustStockRequest(4, new BigDecimal("-3.000"), "regularization"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movementType", is("MODIFICACION")));
    }

    @Test
    void planSlot_returns200() throws Exception {
        String token = loginAsAdmin();
        when(mcpToolWriteService.planSlot(any())).thenReturn(new McpSlotDto(21L, 12, "Rice", new BigDecimal("8"), 2, LocalTime.of(12, 0), LocalTime.of(13, 0), "PENDING"));

        mockMvc.perform(post("/api/mcp/tools/plan-slot")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpPlanSlotRequest(1L, 12, new BigDecimal("8"), 2, "12:00", "13:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotId", is(21)));
    }

    @Test
    void confirmSlot_returns200() throws Exception {
        String token = loginAsAdmin();
        when(mcpToolWriteService.confirmSlot(1L, 21L)).thenReturn(new McpSlotDto(21L, 12, "Rice", new BigDecimal("8"), 2, LocalTime.of(12, 0), LocalTime.of(13, 0), "CONFIRMED"));

        mockMvc.perform(post("/api/mcp/tools/confirm-slot/1/21")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")));
    }

    @Test
    void quarantineBatch_returns200() throws Exception {
        String token = loginAsAdmin();
        when(mcpToolWriteService.quarantineBatch(any())).thenReturn(Map.of("batchId", 8L, "status", "QUARANTINED"));

        mockMvc.perform(post("/api/mcp/tools/quarantine-batch")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpQuarantineRequest(8L, "faulty"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("QUARANTINED")));
    }

    @Test
    void reportIncident_returns200() throws Exception {
        String token = loginAsAdmin();
        when(mcpToolWriteService.reportIncident(any())).thenReturn(Map.of("incidentId", 50L, "status", "ABIERTO"));

        mockMvc.perform(post("/api/mcp/tools/report-incident")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpIncidentRequest("Title", "Desc", "HIGH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ABIERTO")));
    }

    @Test
    void allWriteEndpoints_withoutServiceKey_return403() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(post("/api/mcp/tools/create-order")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(new McpCreateOrderRequest(10, List.of()))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/mcp/tools/quarantine-batch")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}