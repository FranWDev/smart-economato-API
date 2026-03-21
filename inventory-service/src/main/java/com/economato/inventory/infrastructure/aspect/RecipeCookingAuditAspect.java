package com.economato.inventory.infrastructure.aspect;

import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import com.economato.inventory.domain.RecipeCookingAuditable;
import com.economato.inventory.application.dto.event.RecipeCookingAuditEvent;
import com.economato.inventory.application.dto.event.RecipeCookingAuditEvent.DailyConsumption;
import com.economato.inventory.application.dto.request.RecipeCookingRequestDTO;
import com.economato.inventory.application.dto.response.ProductConsumptionResponseDTO;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditEventProducer;
import com.economato.inventory.application.usecase.StockLedgerService;
import com.economato.inventory.domain.model.Recipe;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Aspect
@Component
@Profile({ "!test", "kafka-test" })
@Slf4j
public class RecipeCookingAuditAspect {

    private final RecipeRepository recipeRepository;
    private final SecurityContextHelper securityContextHelper;
    private final AuditEventProducer auditEventProducer;
    private final StockLedgerService stockLedgerService;
    private final ObjectMapper objectMapper;

    public RecipeCookingAuditAspect(
            RecipeRepository recipeRepository,
            SecurityContextHelper securityContextHelper,
            AuditEventProducer auditEventProducer,
            StockLedgerService stockLedgerService,
            ObjectMapper objectMapper) {
        this.recipeRepository = recipeRepository;
        this.securityContextHelper = securityContextHelper;
        this.auditEventProducer = auditEventProducer;
        this.stockLedgerService = stockLedgerService;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(auditable)")
    public Object logCookingAction(ProceedingJoinPoint joinPoint, RecipeCookingAuditable auditable) throws Throwable {
        RecipeCookingRequestDTO cookingRequest = null;

        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof RecipeCookingRequestDTO) {
                cookingRequest = (RecipeCookingRequestDTO) arg;
                break;
            }
        }

        if (cookingRequest == null) {
            log.debug("No se encontró RecipeCookingRequestDTO en los argumentos");
            return joinPoint.proceed();
        }

        Object result = joinPoint.proceed();

        try {

            Recipe recipe = recipeRepository.findByIdWithDetails(cookingRequest.getRecipeId())
                    .orElse(null);

            if (recipe == null) {
                log.warn("Receta no encontrada para auditoría: {}", cookingRequest.getRecipeId());
                return result;
            }

            User user = securityContextHelper.getCurrentUser();

            String componentsState = buildComponentsState(recipe);

            StringBuilder details = new StringBuilder();
            details.append("Receta cocinada: ").append(recipe.getName());
            details.append(" - Cantidad: ").append(cookingRequest.getQuantity());
            if (cookingRequest.getDetails() != null && !cookingRequest.getDetails().isEmpty()) {
                details.append(" - ").append(cookingRequest.getDetails());
            }

            Map<Integer, List<DailyConsumption>> productHistories =
                    buildProductHistories(recipe);

            RecipeCookingAuditEvent event = RecipeCookingAuditEvent.builder()
                    .recipeId(recipe.getId())
                    .recipeName(recipe.getName())
                    .userId(user != null ? user.getId() : null)
                    .userName(user != null ? user.getName() : "Sistema")
                    .quantityCooked(cookingRequest.getQuantity())
                    .details(details.toString())
                    .componentsState(componentsState)
                    .cookingDate(LocalDateTime.now())
                    .correlationId(cookingRequest.getCorrelationId())
                    .productHistories(productHistories)
                    .build();

            auditEventProducer.publishRecipeCookingAudit(event);

            log.info("Evento de auditoría de cocinado publicado: receta={}, cantidad={}, usuario={}",
                    recipe.getName(), cookingRequest.getQuantity(), user != null ? user.getName() : "Sistema");

        } catch (Exception e) {

            log.error("Error al publicar evento de auditoría de cocinado: {}", e.getMessage(), e);
        }

        return result;
    }

    private String buildComponentsState(Recipe recipe) {
        try {
            Map<String, Object> state = new HashMap<>();

            if (recipe.getComponents() != null && !recipe.getComponents().isEmpty()) {
                var components = recipe.getComponents().stream()
                        .map(comp -> {
                            Map<String, Object> componentData = new HashMap<>();
                            componentData.put("productId", comp.getProduct().getId());
                            componentData.put("productName", comp.getProduct().getName());
                            componentData.put("quantity", comp.getQuantity());
                            return componentData;
                        })
                        .collect(Collectors.toList());

                state.put("components", components);
            }

            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            log.error("Error al construir estado de componentes: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Consulta los últimos 90 días de consumo para cada componente de la receta y
     * devuelve el desglose diario agrupado por productId.
     *
     * Estos datos se incluyen en el evento de cocinado de Kafka para que el servicio
     * predictor pueda ejecutar Prophet sin realizar llamadas HTTP de vuelta al backend.
     */
    private Map<Integer, List<DailyConsumption>> buildProductHistories(Recipe recipe) {
        if (recipe.getComponents() == null || recipe.getComponents().isEmpty()) {
            return Collections.emptyMap();
        }

        LocalDateTime end   = LocalDateTime.now();
        LocalDateTime start = end.minusDays(90).withHour(0).withMinute(0).withSecond(0).withNano(0);

        log.info("Generando historial embebido: rango [{} - {}]", start, end);

        Map<Integer, List<DailyConsumption>> result = new HashMap<>();

        List<Integer> productIds = recipe.getComponents().stream()
                .map(comp -> comp.getProduct().getId())
                .collect(Collectors.toList());

        Map<Integer, ProductConsumptionResponseDTO> batchResults;
        try {
            batchResults = stockLedgerService.getProductConsumptionBatch(productIds, start, end);
        } catch (Exception e) {
            log.error("Error al obtener historial de consumo en lote: {}", e.getMessage());
            batchResults = Collections.emptyMap();
        }

        for (Integer productId : productIds) {
            ProductConsumptionResponseDTO dto = batchResults.get(productId);
            if (dto != null && dto.getBreakdown() != null) {
                List<DailyConsumption> history = dto.getBreakdown().stream()
                        .map(d -> new DailyConsumption(d.getDate(), d.getConsumed()))
                        .collect(Collectors.toList());
                result.put(productId, history);
            } else {
                result.put(productId, Collections.emptyList());
            }
        }

        return result;
    }

}
