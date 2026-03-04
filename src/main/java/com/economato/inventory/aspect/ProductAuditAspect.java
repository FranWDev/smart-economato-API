package com.economato.inventory.aspect;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.economato.inventory.annotation.ProductAuditable;
import com.economato.inventory.dto.event.InventoryAuditEvent;
import com.economato.inventory.dto.request.ProductRequestDTO;
import com.economato.inventory.kafka.producer.AuditEventProducer;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.security.SecurityContextHelper;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Aspecto para auditoría de productos.
 * Publica eventos en Kafka de forma asíncrona y no bloqueante.
 */
@Aspect
@Component
@Profile({ "!test", "kafka-test" })
@Slf4j
public class ProductAuditAspect {

    private final ProductRepository productRepository;
    private final SecurityContextHelper securityContextHelper;
    private final AuditEventProducer auditEventProducer;
    private final ObjectMapper objectMapper;

    public ProductAuditAspect(
            ProductRepository productRepository,
            SecurityContextHelper securityContextHelper,
            AuditEventProducer auditEventProducer,
            ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.securityContextHelper = securityContextHelper;
        this.auditEventProducer = auditEventProducer;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(auditable)")
    public Object logAroundAuditableMethod(ProceedingJoinPoint joinPoint, ProductAuditable auditable) throws Throwable {
        // Extraer DTO y ID del método
        ProductRequestDTO foundDto = null;
        Integer productId = null;
        Boolean hiddenRequested = null;

        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof ProductRequestDTO) {
                foundDto = (ProductRequestDTO) arg;
            } else if (arg instanceof Integer) {
                productId = (Integer) arg;
            } else if (arg instanceof Boolean) {
                hiddenRequested = (Boolean) arg;
            }
        }

        final ProductRequestDTO dto = foundDto;

        // Capturar el estado ANTES del cambio y serializarlo inmediatamente
        String previousState = null;
        if (productId != null) {
            Product productBefore = productRepository.findById(productId).orElse(null);
            if (productBefore != null) {
                previousState = buildProductState(productBefore);
            }
        }

        Object result = joinPoint.proceed();

        try {
            Product productAfter = null;

            if (productId != null) {
                productAfter = productRepository.findById(productId).orElse(null);
            }

            // Para CREATE, buscar por nombre
            if (productAfter == null && dto != null && dto.getName() != null) {
                productAfter = productRepository.findByNameContainingIgnoreCase(dto.getName())
                        .stream()
                        .filter(p -> p.getName().equals(dto.getName()))
                        .findFirst()
                        .orElse(null);
            }

            if (productAfter == null) {
                log.warn("Producto no encontrado para auditoría. id={}, dtoName={}",
                        productId,
                        dto != null ? dto.getName() : null);
                return result;
            }

            User user = securityContextHelper.getCurrentUser();

            String actionDescription = resolveActionDescription(auditable.action(), hiddenRequested);

            // Mapear acción de auditoría a tipo de movimiento válido
            String movementType = mapActionToMovementType(actionDescription);

            // Construir estado posterior
            String newState = buildProductState(productAfter);

            // Construir evento de auditoría
            InventoryAuditEvent event = InventoryAuditEvent.builder()
                    .productId(productAfter.getId())
                    .productName(productAfter.getName())
                    .userId(user != null ? user.getId() : null)
                    .userName(user != null ? user.getName() : "Sistema")
                    .movementType(movementType)
                        .quantity(dto != null && dto.getCurrentStock() != null ? dto.getCurrentStock()
                            : java.math.BigDecimal.ZERO)
                        .actionDescription(actionDescription)
                    .previousState(previousState)
                    .newState(newState)
                    .movementDate(LocalDateTime.now())
                    .build();

            // Publicar evento en Kafka de forma asíncrona
            auditEventProducer.publishInventoryAudit(event);

            log.info("Evento de auditoría de producto publicado: producto={}, acción={}",
                    productAfter.getId(), auditable.action());

        } catch (Exception e) {
            // No propagar excepción para no afectar la operación principal
            log.error("Error al publicar evento de auditoría de producto: {}", e.getMessage(), e);
        }

        return result;
    }

    private String buildProductState(Product product) {
        try {
            Map<String, Object> state = new HashMap<>();
            state.put("id", product.getId());
            state.put("nombre", product.getName());
            state.put("tipo", product.getType());
            state.put("unidad", product.getUnit());
            state.put("precioUnitario", product.getUnitPrice());
            state.put("codigoProducto", product.getProductCode());
            state.put("stockActual", product.getCurrentStock());
            state.put("oculto", product.isHidden());
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            log.error("Error al serializar estado del producto: {}", e.getMessage());
            return "Error al capturar estado";
        }
    }

    private String resolveActionDescription(String action, Boolean hiddenRequested) {
        if ("TOGGLE_HIDDEN".equalsIgnoreCase(action) && hiddenRequested != null) {
            return hiddenRequested ? "HIDE_PRODUCT" : "SHOW_PRODUCT";
        }
        return action;
    }

    /**
     * Mapea acciones de auditoría a tipos de movimiento válidos.
     * Valores válidos: IN, OUT, ADJUSTMENT, RECEPTION, PRODUCTION
     */
    private String mapActionToMovementType(String action) {
        if (action == null) {
            return "ADJUSTMENT";
        }

        return switch (action.toUpperCase()) {
            case "CREATE_PRODUCT", "CREATE_RECIPE" -> "PRODUCCION";
            case "UPDATE_PRODUCT", "UPDATE_RECIPE" -> "AJUSTE";
            case "DELETE_PRODUCT", "DELETE_RECIPE" -> "SALIDA";
            case "HIDE_PRODUCT", "SHOW_PRODUCT", "TOGGLE_HIDDEN" -> "AJUSTE";
            default -> "AJUSTE";
        };
    }
}
