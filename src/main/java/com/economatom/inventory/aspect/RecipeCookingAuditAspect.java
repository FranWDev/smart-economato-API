package com.economatom.inventory.aspect;

import com.economatom.inventory.annotation.RecipeCookingAuditable;
import com.economatom.inventory.dto.event.RecipeCookingAuditEvent;
import com.economatom.inventory.dto.request.RecipeCookingRequestDTO;
import com.economatom.inventory.kafka.producer.AuditEventProducer;
import com.economatom.inventory.model.Recipe;
import com.economatom.inventory.model.User;
import com.economatom.inventory.repository.RecipeRepository;
import com.economatom.inventory.repository.UserRepository;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Aspect
@Component
@Profile("!test")
public class RecipeCookingAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(RecipeCookingAuditAspect.class);
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;
    private final AuditEventProducer auditEventProducer;

    public RecipeCookingAuditAspect(
            RecipeRepository recipeRepository,
            UserRepository userRepository,
            AuditEventProducer auditEventProducer) {
        this.recipeRepository = recipeRepository;
        this.userRepository = userRepository;
        this.auditEventProducer = auditEventProducer;
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

            User user = getCurrentUser();

            String componentsState = buildComponentsState(recipe);

            StringBuilder details = new StringBuilder();
            details.append("Receta cocinada: ").append(recipe.getName());
            details.append(" - Cantidad: ").append(cookingRequest.getQuantity());
            if (cookingRequest.getDetails() != null && !cookingRequest.getDetails().isEmpty()) {
                details.append(" - ").append(cookingRequest.getDetails());
            }

            RecipeCookingAuditEvent event = RecipeCookingAuditEvent.builder()
                    .recipeId(recipe.getId())
                    .recipeName(recipe.getName())
                    .userId(user != null ? user.getId() : null)
                    .userName(user != null ? user.getName() : "Sistema")
                    .quantityCooked(cookingRequest.getQuantity())
                    .details(details.toString())
                    .componentsState(componentsState)
                    .cookingDate(LocalDateTime.now())
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

    private User getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal())) {
                String username = authentication.getName();
                return userRepository.findByName(username).orElse(null);
            }
        } catch (Exception e) {
            log.debug("No se pudo obtener usuario actual: {}", e.getMessage());
        }
        return null;
    }
}
