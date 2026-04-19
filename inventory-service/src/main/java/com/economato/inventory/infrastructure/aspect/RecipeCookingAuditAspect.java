package com.economato.inventory.infrastructure.aspect;

import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.economato.inventory.infrastructure.config.security.SecurityContextHelper;
import com.economato.inventory.domain.RecipeCookingAuditable;
import com.economato.inventory.application.dto.event.RecipeCookingAuditEvent;
import com.economato.inventory.application.dto.request.RecipeCookingRequestDTO;
import com.economato.inventory.application.dto.response.RecipeResponseDTO;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditEventProducer;
import com.economato.inventory.domain.model.Recipe;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.RecipeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    private final ObjectMapper objectMapper;

    public RecipeCookingAuditAspect(
            RecipeRepository recipeRepository,
            SecurityContextHelper securityContextHelper,
            AuditEventProducer auditEventProducer,
            ObjectMapper objectMapper) {
        this.recipeRepository = recipeRepository;
        this.securityContextHelper = securityContextHelper;
        this.auditEventProducer = auditEventProducer;
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
            if (result instanceof RecipeResponseDTO) {
                publishAudit((RecipeResponseDTO) result, cookingRequest);
            } else {
                Recipe recipe = recipeRepository.findByIdWithDetails(cookingRequest.getRecipeId())
                        .orElse(null);

                if (recipe != null) {
                    publishAuditFromEntity(recipe, cookingRequest);
                }
            }

        } catch (Exception e) {
            log.error("Error al publicar evento de auditoría de cocinado: {}", e.getMessage(), e);
        }

        return result;
    }

    private void publishAudit(RecipeResponseDTO recipe, RecipeCookingRequestDTO request) {
        User user = securityContextHelper.getCurrentUser();
        String componentsState = buildComponentsStateFromDto(recipe);

        String details = "Receta cocinada: " + recipe.getName() + " - Cantidad: " + request.getQuantity();
        if (request.getDetails() != null && !request.getDetails().isEmpty()) {
            details += " - " + request.getDetails();
        }

        RecipeCookingAuditEvent event = RecipeCookingAuditEvent.builder()
                .recipeId(recipe.getId())
                .recipeName(recipe.getName())
                .userId(user != null ? user.getId() : null)
                .userName(user != null ? user.getName() : "Sistema")
                .quantityCooked(request.getQuantity())
                .details(details)
                .componentsState(componentsState)
                .cookingDate(LocalDateTime.now())
                .correlationId(request.getCorrelationId())
                .sellingPrice(recipe.getSellingPrice())
                .totalGrossCost(recipe.getTotalCost())
                .totalNetCost(calculateTotalNetCostFromDto(recipe))
                .build();


        auditEventProducer.publishRecipeCookingAudit(event);
    }

    private void publishAuditFromEntity(Recipe recipe, RecipeCookingRequestDTO request) {
        User user = securityContextHelper.getCurrentUser();
        String componentsState = buildComponentsStateFromEntity(recipe);

        String details = "Receta cocinada: " + recipe.getName() + " - Cantidad: " + request.getQuantity();

        RecipeCookingAuditEvent event = RecipeCookingAuditEvent.builder()
                .recipeId(recipe.getId())
                .recipeName(recipe.getName())
                .userId(user != null ? user.getId() : null)
                .userName(user != null ? user.getName() : "Sistema")
                .quantityCooked(request.getQuantity())
                .details(details)
                .componentsState(componentsState)
                .cookingDate(LocalDateTime.now())
                .correlationId(request.getCorrelationId())
                .sellingPrice(recipe.getSellingPrice())
                .totalGrossCost(recipe.getTotalCost())
                .totalNetCost(calculateTotalNetCostFromEntity(recipe))
                .build();


        auditEventProducer.publishRecipeCookingAudit(event);
    }

    private String buildComponentsStateFromDto(RecipeResponseDTO recipe) {
        try {
            if (recipe.getComponents() == null || recipe.getComponents().isEmpty()) return "{}";
            
            List<Map<String, Object>> components = recipe.getComponents().stream()
                    .map(comp -> {
                        Map<String, Object> data = new HashMap<>();
                        data.put("productId", comp.getProductId());
                        data.put("productName", comp.getProductName());
                        data.put("quantity", comp.getQuantity()); // Neto
                        data.put("unitPrice", comp.getUnitPrice());
                        data.put("availabilityPercentage", comp.getAvailabilityPercentage() != null ? comp.getAvailabilityPercentage() : 100.0);
                        return data;
                    }).collect(Collectors.toList());

            return objectMapper.writeValueAsString(Map.of("components", components));

        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildComponentsStateFromEntity(Recipe recipe) {
        try {
            if (recipe.getComponents() == null || recipe.getComponents().isEmpty()) return "{}";
            
            List<Map<String, Object>> components = recipe.getComponents().stream()
                    .map(comp -> {
                        Map<String, Object> data = new HashMap<>();
                        data.put("productId", comp.getProduct().getId());
                        data.put("productName", comp.getProduct().getName());
                        data.put("quantity", comp.getQuantity()); // Neto
                        data.put("unitPrice", comp.getProduct().getUnitPrice());
                        data.put("availabilityPercentage", comp.getProduct().getAvailabilityPercentage() != null ? comp.getProduct().getAvailabilityPercentage() : 100.0);
                        return data;
                    }).collect(Collectors.toList());

            return objectMapper.writeValueAsString(Map.of("components", components));

        } catch (Exception e) {
            return "{}";
        }
    }

    private BigDecimal calculateTotalNetCostFromDto(RecipeResponseDTO recipe) {
        if (recipe.getComponents() == null) return BigDecimal.ZERO;
        return recipe.getComponents().stream()
                .map(c -> c.getQuantity().multiply(c.getUnitPrice() != null ? c.getUnitPrice() : BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateTotalNetCostFromEntity(Recipe recipe) {
        if (recipe.getComponents() == null) return BigDecimal.ZERO;
        return recipe.getComponents().stream()
                .map(c -> c.getQuantity().multiply(c.getProduct().getUnitPrice() != null ? c.getProduct().getUnitPrice() : BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

