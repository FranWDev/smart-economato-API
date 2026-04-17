package com.economato.inventory.application.usecase.mcp;

import com.economato.inventory.application.dto.mcp.McpAdjustStockRequest;
import com.economato.inventory.application.dto.mcp.McpCookRecipeRequest;
import com.economato.inventory.application.dto.mcp.McpCreateOrderRequest;
import com.economato.inventory.application.dto.mcp.McpIncidentRequest;
import com.economato.inventory.application.dto.mcp.McpOrderDto;
import com.economato.inventory.application.dto.mcp.McpOrderItemRequest;
import com.economato.inventory.application.dto.mcp.McpPlanSlotRequest;
import com.economato.inventory.application.dto.mcp.McpQuarantineRequest;
import com.economato.inventory.application.dto.mcp.McpRecipeDto;
import com.economato.inventory.application.dto.mcp.McpSlotDto;
import com.economato.inventory.application.dto.request.CreateIncidentRequestDTO;
import com.economato.inventory.application.dto.request.ManualStockAdjustmentRequestDTO;
import com.economato.inventory.application.dto.request.OrderDetailRequestDTO;
import com.economato.inventory.application.dto.request.OrderRequestDTO;
import com.economato.inventory.application.dto.request.RecipeCookingRequestDTO;
import com.economato.inventory.application.dto.response.OrderResponseDTO;
import com.economato.inventory.application.dto.response.RecipeResponseDTO;
import com.economato.inventory.application.dto.response.WeeklyPlanSlotResponseDTO;
import com.economato.inventory.application.usecase.IncidentService;
import com.economato.inventory.application.usecase.OrderService;
import com.economato.inventory.application.usecase.RecipeService;
import com.economato.inventory.application.usecase.StockLedgerService;
import com.economato.inventory.application.usecase.WeeklyPlanService;
import com.economato.inventory.domain.model.MovementType;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class McpToolWriteService {

    private final OrderService orderService;
    private final RecipeService recipeService;
    private final StockLedgerService stockLedgerService;
    private final WeeklyPlanService weeklyPlanService;
    private final IncidentService incidentService;
    private final SecurityContextHelper securityContextHelper;
    private final I18nService i18nService;

    public McpOrderDto createOrder(McpCreateOrderRequest request) {
        User currentUser = requireCurrentUser();
        OrderRequestDTO order = new OrderRequestDTO();
        order.setUserId(currentUser.getId());
        order.setSupplierId(request.supplierId());
        List<OrderDetailRequestDTO> details = request.items().stream()
                .map(this::toOrderDetail)
                .toList();
        order.setDetails(details);

        OrderResponseDTO created = orderService.save(order);
        int itemCount = created.getDetails() == null ? 0 : created.getDetails().size();
        return McpOrderDto.builder()
                .id(created.getId())
                .status(created.getStatus() != null ? created.getStatus().name() : null)
                .totalAmount(created.getTotalPrice())
                .itemCount(itemCount)
                .supplierName(created.getSupplierName())
                .orderDate(created.getOrderDate() != null ? created.getOrderDate().toString() : null)
                .build();
    }

    public McpRecipeDto cookRecipe(McpCookRecipeRequest request) {
        RecipeCookingRequestDTO dto = RecipeCookingRequestDTO.builder()
                .recipeId(request.recipeId())
                .quantity(request.quantity())
                .build();

        RecipeResponseDTO response = recipeService.cookRecipe(dto);
        return McpRecipeDto.builder()
                .id(response.getId())
            .name(response.getName())
                .code(response.getId() != null ? response.getId().toString() : null)
                .cost(response.getTotalCost())
                .allergenCount(response.getAllergens() != null ? response.getAllergens().size() : 0)
                .description(response.getPresentation())
                .preparation(response.getElaboration())
                .build();
    }

    public Map<String, Object> adjustStock(McpAdjustStockRequest request) {
        ManualStockAdjustmentRequestDTO dto = new ManualStockAdjustmentRequestDTO();
        dto.setProductId(request.productId());
        dto.setQuantityDelta(request.quantityDelta());
        dto.setDescription(request.reason());
        dto.setMovementType(MovementType.MODIFICACION);

        var movement = stockLedgerService.processManualAdjustment(dto);
        Map<String, Object> result = new HashMap<>();
        result.put("productId", request.productId());
        result.put("newStock", movement.getResultingStock());
        result.put("movementType", movement.getMovementType().name());
        result.put("transactionId", movement.getId());
        return result;
    }

    public McpSlotDto planSlot(McpPlanSlotRequest request) {
        throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_MCP_PLAN_SLOT_REQUIRES_FLOW));
    }

    public McpSlotDto confirmSlot(Long planId, Long slotId) {
        WeeklyPlanSlotResponseDTO slot = weeklyPlanService.confirmSlot(planId, slotId);
        return new McpSlotDto(
                slot.getId(),
                slot.getRecipeId(),
                slot.getRecipeName(),
                slot.getQuantity(),
                slot.getDayOfWeek(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.getStatus() != null ? slot.getStatus().name() : null
        );
    }

    public Map<String, Object> quarantineBatch(McpQuarantineRequest request) {
        Map<String, Object> result = new HashMap<>();
        result.put("batchId", request.batchId());
        result.put("status", "QUARANTINE_REQUESTED");
        result.put("reason", request.reason());
        return result;
    }

    public Map<String, Object> reportIncident(McpIncidentRequest request) {
        Integer incidentTypeId = mapIncidentTypeId(request.severity());

        CreateIncidentRequestDTO dto = CreateIncidentRequestDTO.builder()
                .incidentTypeId(incidentTypeId)
                .title(request.title())
                .description(request.description())
                .build();

        var created = incidentService.createIncident(dto);
        Map<String, Object> result = new HashMap<>();
        result.put("incidentId", created.getId());
        result.put("status", created.getStatus() != null ? created.getStatus().name() : null);
        return result;
    }

    private Integer mapIncidentTypeId(String severityRaw) {
        if (severityRaw == null || severityRaw.isBlank()) {
            return 1;
        }

        String severity = severityRaw.trim().toUpperCase(Locale.ROOT);
        return switch (severity) {
            case "ALTA", "HIGH" -> 3;
            case "MEDIA", "MEDIUM" -> 2;
            case "BAJA", "LOW" -> 1;
            default -> 1;
        };
    }

    private User requireCurrentUser() {
        User currentUser = securityContextHelper.getCurrentUser();
        if (currentUser == null) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_AUTH_USER_REQUIRED));
        }
        return currentUser;
    }

    private OrderDetailRequestDTO toOrderDetail(McpOrderItemRequest item) {
        OrderDetailRequestDTO detail = new OrderDetailRequestDTO();
        detail.setOrderId(0);
        detail.setProductId(item.productId());
        detail.setQuantity(item.quantity() == null ? BigDecimal.ZERO : item.quantity());
        return detail;
    }
}
