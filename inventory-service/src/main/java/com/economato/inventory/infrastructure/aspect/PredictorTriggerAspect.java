package com.economato.inventory.infrastructure.aspect;

import com.economato.inventory.application.dto.event.StockPredictionEvent;
import com.economato.inventory.application.dto.event.StockPredictionEvent.DailyConsumption;
import com.economato.inventory.application.dto.request.ManualStockAdjustmentRequestDTO;
import com.economato.inventory.application.dto.request.OrderReceptionRequestDTO;
import com.economato.inventory.application.dto.request.OrderReceptionDetailRequestDTO;
import com.economato.inventory.application.dto.request.RecipeCookingRequestDTO;
import com.economato.inventory.application.dto.response.ProductConsumptionResponseDTO.DailyConsumptionDTO;
import com.economato.inventory.application.usecase.StockLedgerService;
import com.economato.inventory.domain.PredictorTrigger;
import com.economato.inventory.domain.model.Recipe;
import com.economato.inventory.domain.model.RecipeCookingAudit;
import com.economato.inventory.domain.model.StockLedger;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.domain.model.WeeklyPlanSlot;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.OrderRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.WeeklyPlanSlotRepository;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Aspect
@Component
@Profile({ "!test", "kafka-test" })
@Slf4j
@RequiredArgsConstructor
public class PredictorTriggerAspect {

    private final StockLedgerService stockLedgerService;
    private final AuditEventProducer auditEventProducer;
    private final RecipeRepository recipeRepository;
    private final OrderRepository orderRepository;
    private final StockLedgerRepository stockLedgerRepository;
    private final RecipeCookingAuditRepository recipeCookingAuditRepository;
    private final WeeklyPlanSlotRepository weeklyPlanSlotRepository;
    private final SecurityContextHelper securityContextHelper;

    @AfterReturning(pointcut = "@annotation(trigger)", returning = "result")
    public void afterModification(JoinPoint joinPoint, PredictorTrigger trigger, Object result) {
        try {
            log.debug("PredictorTrigger detectado: action={}", trigger.action());
            List<Integer> affectedProductIds = resolveAffectedProductIds(joinPoint, trigger, result);

            if (affectedProductIds.isEmpty()) {
                log.warn("No se encontraron productIds afectados para la acción: {}", trigger.action());
                return;
            }

            // Eliminar duplicados y nulos si los hubiera
            affectedProductIds = affectedProductIds.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            // Obtener historiales de 90 días
            LocalDateTime end = LocalDateTime.now();
            LocalDateTime start = end.minusDays(90).withHour(0).withMinute(0).withSecond(0).withNano(0);

            Map<Integer, List<DailyConsumptionDTO>> batchResults =
                    stockLedgerService.getDailyConsumptionBatch(affectedProductIds, start, end);

            Map<Integer, List<DailyConsumption>> productHistories = new HashMap<>();
            for (Integer productId : affectedProductIds) {
                List<DailyConsumptionDTO> breakdown = batchResults.get(productId);
                if (breakdown != null) {
                    List<DailyConsumption> history = breakdown.stream()
                            .map(d -> DailyConsumption.builder()
                                    .date(d.getDate())
                                    .consumed(d.getConsumed())
                                    .build())
                            .collect(Collectors.toList());
                    productHistories.put(productId, history);
                } else {
                    productHistories.put(productId, Collections.emptyList());
                }
            }

            User user = securityContextHelper.getCurrentUser();

            StockPredictionEvent event = StockPredictionEvent.builder()
                    .triggerType(trigger.action())
                    .affectedProductIds(affectedProductIds)
                    .productHistories(productHistories)
                    .timestamp(LocalDateTime.now())
                    .userId(user != null ? user.getId() : null)
                    .userName(user != null ? user.getName() : "Sistema")
                    .build();

            auditEventProducer.publishStockPredictionEvent(event);
            log.info("Evento de predicción publicado para acción: {}, productos={}", 
                    trigger.action(), affectedProductIds);

        } catch (Exception e) {
            log.error("Error al procesar PredictorTrigger para acción {}: {}", 
                    trigger.action(), e.getMessage(), e);
        }
    }

    private List<Integer> resolveAffectedProductIds(JoinPoint joinPoint, PredictorTrigger trigger, Object result) {
        Object[] args = joinPoint.getArgs();
        String action = trigger.action();

        switch (action) {
            case "COOK_RECIPE":
                return extractFromCookRecipe(args);
            case "REVERT_COOKING":
                return extractFromRevertCooking(args);
            case "WEEKLY_PLAN_CONFIRM":
                return extractFromWeeklyPlanConfirm(args);
            case "ORDER_RECEPTION":
                return extractFromOrderReception(args);
            case "MANUAL_ADJUSTMENT":
                return extractFromManualAdjustment(args);
            case "REVERSION":
                return extractFromReversion(args);
            case "SCHEDULED_REFRESH":
                return extractFromScheduledRefresh(result);
            default:
                log.warn("Acción no reconocida por PredictorTriggerAspect: {}", action);
                return Collections.emptyList();
        }
    }

    private List<Integer> extractFromCookRecipe(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof RecipeCookingRequestDTO) {
                RecipeCookingRequestDTO request = (RecipeCookingRequestDTO) arg;
                return recipeRepository.findByIdWithDetails(request.getRecipeId())
                        .map(Recipe::getComponents)
                        .map(components -> components.stream()
                                .map(c -> c.getProduct().getId())
                                .collect(Collectors.toList()))
                        .orElse(Collections.emptyList());
            }
        }
        return Collections.emptyList();
    }

    private List<Integer> extractFromRevertCooking(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Long) {
                Long auditId = (Long) arg;
                return recipeCookingAuditRepository.findById(auditId)
                        .map(RecipeCookingAudit::getRecipe)
                        .map(recipe -> recipe.getComponents().stream()
                                .map(c -> c.getProduct().getId())
                                .collect(Collectors.toList()))
                        .orElse(Collections.emptyList());
            }
        }
        return Collections.emptyList();
    }

    private List<Integer> extractFromWeeklyPlanConfirm(Object[] args) {
        Long slotId = null;
        if (args.length >= 2 && args[1] instanceof Long) {
            slotId = (Long) args[1];
        } else {
            for (Object arg : args) {
                if (arg instanceof Long) {
                    slotId = (Long) arg;
                }
            }
        }

        if (slotId != null) {
            return weeklyPlanSlotRepository.findWithDetailsById(slotId)
                    .map(WeeklyPlanSlot::getRecipe)
                    .map(recipe -> recipe.getComponents().stream()
                            .map(c -> c.getProduct().getId())
                            .collect(Collectors.toList()))
                    .orElse(Collections.emptyList());
        }
        return Collections.emptyList();
    }

    private List<Integer> extractFromOrderReception(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof OrderReceptionRequestDTO) {
                OrderReceptionRequestDTO request = (OrderReceptionRequestDTO) arg;
                if (request.getItems() != null) {
                    return request.getItems().stream()
                            .map(OrderReceptionDetailRequestDTO::getProductId)
                            .collect(Collectors.toList());
                }
            }
        }
        return Collections.emptyList();
    }

    private List<Integer> extractFromManualAdjustment(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof ManualStockAdjustmentRequestDTO) {
                ManualStockAdjustmentRequestDTO request = (ManualStockAdjustmentRequestDTO) arg;
                return Collections.singletonList(request.getProductId());
            }
        }
        return Collections.emptyList();
    }

    private List<Integer> extractFromReversion(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof String) {
                String correlationId = (String) arg;
                List<StockLedger> transactions = stockLedgerRepository.findByCorrelationId(correlationId);
                return transactions.stream()
                        .map(t -> t.getProduct().getId())
                        .distinct()
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

    private List<Integer> extractFromScheduledRefresh(Object result) {
        if (result instanceof List) {
            return (List<Integer>) result;
        }
        return Collections.emptyList();
    }
}
