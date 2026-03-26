package com.economato.inventory.infrastructure.aspect;

import com.economato.inventory.application.dto.event.StockPredictionEvent;
import com.economato.inventory.application.dto.event.StockPredictionEvent.DailyConsumption;
import com.economato.inventory.application.dto.request.ManualStockAdjustmentRequestDTO;
import com.economato.inventory.application.dto.request.OrderReceptionDetailRequestDTO;
import com.economato.inventory.application.dto.request.OrderReceptionRequestDTO;
import com.economato.inventory.application.dto.request.RecipeCookingRequestDTO;
import com.economato.inventory.application.dto.request.BatchMovementItem;
import com.economato.inventory.application.dto.request.BatchStockMovementRequestDTO;
import com.economato.inventory.application.dto.request.StockMovementItemDTO;
import com.economato.inventory.application.dto.response.ProductConsumptionResponseDTO.DailyConsumptionDTO;
import com.economato.inventory.application.dto.response.RecipeResponseDTO;
import com.economato.inventory.application.dto.response.RecipeComponentResponseDTO;
import com.economato.inventory.application.usecase.StockLedgerService;
import com.economato.inventory.domain.PredictorTrigger;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeCookingAuditRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.StockLedgerRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.WeeklyPlanSlotRepository;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
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
    private final StockLedgerRepository stockLedgerRepository;
    private final RecipeCookingAuditRepository recipeCookingAuditRepository;
    private final WeeklyPlanSlotRepository weeklyPlanSlotRepository;
    private final SecurityContextHelper securityContextHelper;

    @Around("@annotation(trigger)")
    public Object aroundModification(ProceedingJoinPoint joinPoint, PredictorTrigger trigger) throws Throwable {
        String action = trigger.action();
        List<Integer> affectedProductIds = new ArrayList<>();

        if ("REVERT_COOKING".equals(action)) {
            affectedProductIds = extractFromRevertCooking(joinPoint.getArgs());
        }

        Object result = joinPoint.proceed();

        try {
            log.debug("PredictorTrigger detectado: action={}", action);

            if (!"REVERT_COOKING".equals(action)) {
                affectedProductIds = resolveAffectedProductIds(joinPoint, trigger, result);
            }

            if (affectedProductIds == null || affectedProductIds.isEmpty()) {
                log.warn("No se encontraron productIds afectados para la acción: {}", action);
                return result;
            }

            List<Integer> finalIds = affectedProductIds.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            if (finalIds.isEmpty()) return result;

            LocalDateTime end = LocalDateTime.now();
            LocalDateTime start = end.minusDays(90).withHour(0).withMinute(0).withSecond(0).withNano(0);

            Map<Integer, List<DailyConsumptionDTO>> batchResults =
                    stockLedgerService.getDailyConsumptionBatch(finalIds, start, end);

            Map<Integer, List<DailyConsumption>> productHistories = new HashMap<>();
            for (Integer productId : finalIds) {
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
                    .triggerType(action)
                    .affectedProductIds(finalIds)
                    .productHistories(productHistories)
                    .timestamp(LocalDateTime.now())
                    .userId(user != null ? user.getId() : null)
                    .userName(user != null ? user.getName() : "Sistema")
                    .build();

            auditEventProducer.publishStockPredictionEvent(event);
            log.info("Evento de predicción publicado para acción: {}, productos={}", 
                    action, finalIds);

        } catch (Exception e) {
            log.error("Error al procesar PredictorTrigger para acción {}: {}", 
                    action, e.getMessage(), e);
        }

        return result;
    }

    private List<Integer> resolveAffectedProductIds(ProceedingJoinPoint joinPoint, PredictorTrigger trigger, Object result) {
        Object[] args = joinPoint.getArgs();
        String action = trigger.action();

        switch (action) {
            case "COOK_RECIPE":
                return extractFromCookRecipe(args, result);
            case "WEEKLY_PLAN_CONFIRM":
                return extractFromWeeklyPlanConfirm(args);
            case "ORDER_RECEPTION":
                return extractFromOrderReception(args);
            case "MANUAL_ADJUSTMENT":
                return extractFromManualAdjustment(args);
            case "REVERSION":
                return extractFromReversion(args);
            case "BATCH_MOVEMENT":
                return extractFromBatchMovement(args);
            default:
                log.warn("Acción no reconocida por PredictorTriggerAspect: {}", action);
                return Collections.emptyList();
        }
    }

    private List<Integer> extractFromBatchMovement(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof BatchStockMovementRequestDTO) {
                BatchStockMovementRequestDTO request = (BatchStockMovementRequestDTO) arg;
                if (request.getMovements() != null) {
                    return request.getMovements().stream()
                            .map(StockMovementItemDTO::getProductId)
                            .collect(Collectors.toList());
                }
            } else if (arg instanceof List) {
                List<?> list = (List<?>) arg;
                if (!list.isEmpty() && list.get(0) instanceof BatchMovementItem) {
                    return list.stream()
                            .map(item -> ((BatchMovementItem) item).getProductId())
                            .collect(Collectors.toList());
                }
            }
        }
        return Collections.emptyList();
    }

    private List<Integer> extractFromCookRecipe(Object[] args, Object result) {
        if (result instanceof RecipeResponseDTO) {
            RecipeResponseDTO recipe = (RecipeResponseDTO) result;
            if (recipe.getComponents() != null) {
                return recipe.getComponents().stream()
                        .map(RecipeComponentResponseDTO::getProductId)
                        .collect(Collectors.toList());
            }
        }

        for (Object arg : args) {
            if (arg instanceof RecipeCookingRequestDTO) {
                RecipeCookingRequestDTO request = (RecipeCookingRequestDTO) arg;
                return recipeRepository.findProductIdsByRecipeId(request.getRecipeId());
            }
        }
        return Collections.emptyList();
    }

    private List<Integer> extractFromRevertCooking(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Long) {
                Long auditId = (Long) arg;
                return recipeCookingAuditRepository.findProductIdsByAuditId(auditId);
            }
        }
        return Collections.emptyList();
    }

    private List<Integer> extractFromWeeklyPlanConfirm(Object[] args) {
        // confirmSlot(Long planId, Long slotId) — necesitamos el segundo Long (slotId)
        Long slotId = null;
        int longCount = 0;
        for (Object arg : args) {
            if (arg instanceof Long) {
                longCount++;
                if (longCount == 2) {
                    slotId = (Long) arg;
                    break;
                }
            }
        }
        if (slotId != null) {
            return weeklyPlanSlotRepository.findProductIdsBySlotId(slotId);
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
                return Collections.singletonList(((ManualStockAdjustmentRequestDTO) arg).getProductId());
            }
        }
        return Collections.emptyList();
    }

    private List<Integer> extractFromReversion(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof String) {
                String correlationId = (String) arg;
                return stockLedgerRepository.findProductIdsByCorrelationId(correlationId);
            }
        }
        return Collections.emptyList();
    }
}
