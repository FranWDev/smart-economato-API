package com.economato.inventory.aspect;

import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.economato.inventory.annotation.RecipeAuditable;
import com.economato.inventory.dto.event.RecipeAuditEvent;
import com.economato.inventory.dto.request.RecipeRequestDTO;
import com.economato.inventory.kafka.producer.AuditEventProducer;
import com.economato.inventory.model.Recipe;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.RecipeRepository;
import com.economato.inventory.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aspecto para auditoría de recetas.
 * Publica eventos en Kafka de forma asíncrona y no bloqueante.
 */
@Aspect
@Component
@Profile("!test")
public class RecipeAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(RecipeAuditAspect.class);
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;
    private final AuditEventProducer auditEventProducer;

    public RecipeAuditAspect(
            RecipeRepository recipeRepository,
            UserRepository userRepository,
            AuditEventProducer auditEventProducer) {
        this.recipeRepository = recipeRepository;
        this.userRepository = userRepository;
        this.auditEventProducer = auditEventProducer;
    }

    @Around("@annotation(auditable)")
    public Object logRecipeAction(ProceedingJoinPoint joinPoint, RecipeAuditable auditable) throws Throwable {
        // Extraer DTO y ID del método
        RecipeRequestDTO foundDto = null;
        Integer recipeId = null;

        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof RecipeRequestDTO) {
                foundDto = (RecipeRequestDTO) arg;
            } else if (arg instanceof Integer) {
                recipeId = (Integer) arg;
            }
        }

        final RecipeRequestDTO dto = foundDto;

        if (dto == null) {
            log.debug("No se encontró RecipeRequestDTO en los argumentos");
            return joinPoint.proceed();
        }

        // Capturar el estado ANTES del cambio y serializarlo inmediatamente
        String previousState = null;
        if (recipeId != null) {
            Recipe recipeBefore = recipeRepository.findByIdWithDetails(recipeId).orElse(null);
            if (recipeBefore != null) {
                previousState = buildRecipeState(recipeBefore);
            }
        }

        Object result = joinPoint.proceed();

        try {
            Recipe recipeAfter = null;

            if (recipeId != null) {
                recipeAfter = recipeRepository.findByIdWithDetails(recipeId).orElse(null);
            }

            // Para CREATE, buscar por nombre
            if (recipeAfter == null && dto.getName() != null) {
                recipeAfter = recipeRepository.findByName(dto.getName()).orElse(null);
            }

            if (recipeAfter == null) {
                log.warn("Receta no encontrada para auditoría: {}", dto.getName());
                return result;
            }

            User user = getCurrentUser();

            // Construir detalles de la auditoría
            StringBuilder details = new StringBuilder();
            details.append("Nombre: ").append(dto.getName()).append("; ");
            details.append("Elaboración: ")
                    .append(dto.getElaboration() != null
                            ? dto.getElaboration().substring(0, Math.min(100, dto.getElaboration().length()))
                            : "N/A")
                    .append("; ");
            details.append("Presentación: ")
                    .append(dto.getPresentation() != null
                            ? dto.getPresentation().substring(0, Math.min(100, dto.getPresentation().length()))
                            : "N/A")
                    .append("; ");
            details.append("Componentes: ").append(dto.getComponents() != null ? dto.getComponents().size() : 0);
            if (dto.getAllergenIds() != null && !dto.getAllergenIds().isEmpty()) {
                details.append("; Alérgenos: ").append(dto.getAllergenIds());
            }

            // Construir estado posterior
            String newState = buildRecipeState(recipeAfter);

            // Construir evento de auditoría
            RecipeAuditEvent event = RecipeAuditEvent.builder()
                    .recipeId(recipeAfter.getId())
                    .recipeName(recipeAfter.getName())
                    .userId(user != null ? user.getId() : null)
                    .userName(user != null ? user.getName() : "Sistema")
                    .action(auditable.action())
                    .details(details.toString())
                    .previousState(previousState)
                    .newState(newState)
                    .auditDate(LocalDateTime.now())
                    .build();

            // Publicar evento en Kafka de forma asíncrona
            auditEventProducer.publishRecipeAudit(event);

            log.info("Evento de auditoría de receta publicado: receta={}, acción={}",
                    recipeAfter.getId(), auditable.action());

        } catch (Exception e) {
            // No propagar excepción para no afectar la operación principal
            log.error("Error al publicar evento de auditoría de receta: {}", e.getMessage(), e);
        }

        return result;
    }

    private String buildRecipeState(Recipe recipe) {
        try {
            Map<String, Object> state = new HashMap<>();
            state.put("id", recipe.getId());
            state.put("nombre", recipe.getName());
            state.put("elaboracion", recipe.getElaboration());
            state.put("presentacion", recipe.getPresentation());
            state.put("costeTotal", recipe.getTotalCost());
            state.put("componentes", recipe.getComponents().stream()
                    .map(c -> Map.of(
                            "productoId", c.getProduct().getId(),
                            "productoNombre", c.getProduct().getName(),
                            "cantidad", c.getQuantity()))
                    .collect(Collectors.toList()));
            state.put("alergenos", recipe.getAllergens().stream()
                    .map(a -> a.getName())
                    .collect(Collectors.toList()));
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            log.error("Error al serializar estado de la receta: {}", e.getMessage());
            return "Error al capturar estado";
        }
    }

    private User getCurrentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                String username = auth.getName();
                return userRepository.findByName(username).orElse(null);
            }
        } catch (Exception e) {
            log.debug("No se pudo obtener usuario autenticado: {}", e.getMessage());
        }
        return null;
    }
}
