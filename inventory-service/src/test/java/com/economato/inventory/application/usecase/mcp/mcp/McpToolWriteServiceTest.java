package com.economato.inventory.application.usecase.mcp.mcp;
import com.economato.inventory.domain.model.product.Supplier;
import com.economato.inventory.application.dto.incident.request.CreateIncidentRequestDTO;
import com.economato.inventory.application.dto.order.request.OrderRequestDTO;

import com.economato.inventory.application.dto.stock.mcp.McpAdjustStockRequest;
import com.economato.inventory.application.dto.order.mcp.McpCreateOrderRequest;
import com.economato.inventory.application.dto.incident.mcp.McpIncidentRequest;
import com.economato.inventory.application.dto.order.mcp.McpOrderDto;
import com.economato.inventory.application.dto.order.mcp.McpOrderItemRequest;
import com.economato.inventory.application.dto.incident.response.IncidentResponseDTO;
import com.economato.inventory.application.dto.order.response.OrderDetailResponseDTO;
import com.economato.inventory.application.dto.order.response.OrderResponseDTO;
import com.economato.inventory.application.dto.weeklyplan.response.WeeklyPlanSlotResponseDTO;
import com.economato.inventory.application.usecase.incident.IncidentService;
import com.economato.inventory.application.usecase.order.OrderService;
import com.economato.inventory.application.usecase.recipe.RecipeService;
import com.economato.inventory.application.usecase.ledger.StockLedgerService;
import com.economato.inventory.application.usecase.weeklyplan.WeeklyPlanService;
import com.economato.inventory.domain.model.incident.IncidentStatus;
import com.economato.inventory.domain.model.shared.MovementType;
import com.economato.inventory.domain.model.order.OrderStatus;
import com.economato.inventory.domain.model.ledger.StockLedger;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.domain.model.weeklyplan.WeeklyPlanSlotStatus;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.InvalidOperationException;
import com.economato.inventory.infrastructure.config.shared.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpToolWriteServiceTest {

    @Mock
    private OrderService orderService;
    @Mock
    private RecipeService recipeService;
    @Mock
    private StockLedgerService stockLedgerService;
    @Mock
    private WeeklyPlanService weeklyPlanService;
    @Mock
    private IncidentService incidentService;
    @Mock
    private SecurityContextHelper securityContextHelper;
    @Mock
    private I18nService i18nService;

    @InjectMocks
    private McpToolWriteService service;

    @Test
    void createOrder_WhenNoAuthenticatedUser_ShouldThrow() {
        when(securityContextHelper.getCurrentUser()).thenReturn(null);

        McpCreateOrderRequest request = new McpCreateOrderRequest(10, List.of());

        when(i18nService.getMessage(any())).thenReturn("Error message");
        assertThrows(InvalidOperationException.class, () -> service.createOrder(request));
    }

    @Test
    void createOrder_ShouldMapRequestAndResponse() {
        User user = new User();
        user.setId(7);
        when(securityContextHelper.getCurrentUser()).thenReturn(user);

        OrderResponseDTO response = new OrderResponseDTO();
        response.setId(11);
        response.setStatus(OrderStatus.CREATED);
        response.setTotalPrice(new BigDecimal("25.50"));
        response.setSupplierName("Supplier A");
        response.setOrderDate(LocalDateTime.of(2025, 1, 10, 9, 30));
        response.setDetails(List.of(new OrderDetailResponseDTO()));
        when(orderService.save(any())).thenReturn(response);

        McpCreateOrderRequest request = new McpCreateOrderRequest(
                10,
                List.of(new McpOrderItemRequest(3, new BigDecimal("2.000"))));

        McpOrderDto result = service.createOrder(request);

        assertEquals(11, result.getId());
        assertEquals("CREATED", result.getStatus());
        assertEquals(new BigDecimal("25.50"), result.getTotalAmount());
        assertEquals(1, result.getItemCount());
        assertEquals("Supplier A", result.getSupplierName());

        ArgumentCaptor<OrderRequestDTO> captor = ArgumentCaptor
                .forClass(OrderRequestDTO.class);
        verify(orderService).save(captor.capture());
        assertEquals(7, captor.getValue().getUserId());
        assertEquals(10, captor.getValue().getSupplierId());
        assertEquals(1, captor.getValue().getDetails().size());
        assertEquals(3, captor.getValue().getDetails().get(0).getProductId());
    }

    @Test
    void adjustStock_ShouldReturnMovementSummary() {
        StockLedger movement = StockLedger.builder()
                .id(99L)
                .movementType(MovementType.MODIFICACION)
                .resultingStock(new BigDecimal("17.000"))
                .build();
        when(stockLedgerService.processManualAdjustment(any())).thenReturn(movement);

        Map<String, Object> result = service.adjustStock(
                new McpAdjustStockRequest(4, new BigDecimal("-3.000"), "regularization"));

        assertEquals(4, result.get("productId"));
        assertEquals(new BigDecimal("17.000"), result.get("newStock"));
        assertEquals("MODIFICACION", result.get("movementType"));
        assertEquals(99L, result.get("transactionId"));
    }

    @Test
    void confirmSlot_ShouldMapServiceResponse() {
        WeeklyPlanSlotResponseDTO slot = new WeeklyPlanSlotResponseDTO();
        slot.setId(21L);
        slot.setRecipeId(12);
        slot.setRecipeName("Rice");
        slot.setQuantity(new BigDecimal("8"));
        slot.setDayOfWeek(2);
        slot.setStartTime(LocalTime.of(12, 0));
        slot.setEndTime(LocalTime.of(13, 0));
        slot.setStatus(WeeklyPlanSlotStatus.CONFIRMED);
        when(weeklyPlanService.confirmSlot(1L, 21L)).thenReturn(slot);

        var result = service.confirmSlot(1L, 21L);

        assertEquals(21L, result.slotId());
        assertEquals(12, result.recipeId());
        assertEquals("CONFIRMED", result.status());
    }

    @Test
    void reportIncident_ShouldReturnCreatedIncidentIdAndStatus() {
        IncidentResponseDTO response = IncidentResponseDTO.builder()
                .id(50L)
                .status(IncidentStatus.ABIERTO)
                .build();
        when(incidentService.createIncident(any())).thenReturn(response);

        Map<String, Object> result = service.reportIncident(new McpIncidentRequest("Title", "Desc", "HIGH"));

        assertEquals(50L, result.get("incidentId"));
        assertEquals("ABIERTO", result.get("status"));

        ArgumentCaptor<CreateIncidentRequestDTO> captor = ArgumentCaptor
                .forClass(CreateIncidentRequestDTO.class);
        verify(incidentService).createIncident(captor.capture());
        assertEquals(3, captor.getValue().getIncidentTypeId());
    }

    @Test
    void reportIncident_ShouldMapSpanishSeverityValues() {
        IncidentResponseDTO response = IncidentResponseDTO.builder()
                .id(51L)
                .status(IncidentStatus.ABIERTO)
                .build();
        when(incidentService.createIncident(any())).thenReturn(response);

        service.reportIncident(new McpIncidentRequest("Title", "Desc", "MEDIA"));

        ArgumentCaptor<CreateIncidentRequestDTO> captor = ArgumentCaptor
                .forClass(CreateIncidentRequestDTO.class);
        verify(incidentService).createIncident(captor.capture());
        assertEquals(2, captor.getValue().getIncidentTypeId());
    }
}