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

import com.economato.inventory.annotation.ProductAuditable;
import com.economato.inventory.dto.event.InventoryAuditEvent;
import com.economato.inventory.dto.request.ProductRequestDTO;
import com.economato.inventory.kafka.producer.AuditEventProducer;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.ProductRepository;
import com.economato.inventory.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Aspecto para auditoría de productos.
 * Publica eventos en Kafka de forma asíncrona y no bloqueante.
 */
@Aspect
@Component
@Profile("!test")
public class ProductAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(ProductAuditAspect.class);
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AuditEventProducer auditEventProducer;

    public ProductAuditAspect(
            ProductRepository productRepository,
            UserRepository userRepository,
            AuditEventProducer auditEventProducer) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.auditEventProducer = auditEventProducer;
    }

    @Around("@annotation(auditable)")
    public Object logAroundAuditableMethod(ProceedingJoinPoint joinPoint, ProductAuditable auditable) throws Throwable {
        // Extraer DTO y ID del método
        ProductRequestDTO foundDto = null;
        Integer productId = null;

        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof ProductRequestDTO) {
                foundDto = (ProductRequestDTO) arg;
            } else if (arg instanceof Integer) {
                productId = (Integer) arg;
            }
        }

        final ProductRequestDTO dto = foundDto;

        if (dto == null) {
            log.debug("No se encontró ProductRequestDTO en los argumentos");
            return joinPoint.proceed();
        }

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
            if (productAfter == null && dto.getName() != null) {
                var allProducts = productRepository.findAll();
                productAfter = allProducts.stream()
                        .filter(p -> p.getName().equals(dto.getName()))
                        .findFirst()
                        .orElse(null);
            }

            if (productAfter == null) {
                log.warn("Producto no encontrado para auditoría: {}", dto.getName());
                return result;
            }

            User user = getCurrentUser();

            // Mapear acción de auditoría a tipo de movimiento válido
            String movementType = mapActionToMovementType(auditable.action());

            // Construir estado posterior
            String newState = buildProductState(productAfter);

            // Construir evento de auditoría
            InventoryAuditEvent event = InventoryAuditEvent.builder()
                    .productId(productAfter.getId())
                    .productName(productAfter.getName())
                    .userId(user != null ? user.getId() : null)
                    .userName(user != null ? user.getName() : "Sistema")
                    .movementType(movementType)
                    .quantity(dto.getCurrentStock() != null ? dto.getCurrentStock() : java.math.BigDecimal.ZERO)
                    .actionDescription(auditable.action())
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
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            log.error("Error al serializar estado del producto: {}", e.getMessage());
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

    /**
     * Mapea acciones de auditoría a tipos de movimiento válidos.
     * Valores válidos: IN, OUT, ADJUSTMENT, RECEPTION, PRODUCTION
     */
    private String mapActionToMovementType(String action) {
        if (action == null) {
            return "ADJUSTMENT";
        }

        return switch (action.toUpperCase()) {
            case "CREATE_PRODUCT", "CREATE_RECIPE" -> "PRODUCTION";
            case "UPDATE_PRODUCT", "UPDATE_RECIPE" -> "ADJUSTMENT";
            case "DELETE_PRODUCT", "DELETE_RECIPE" -> "OUT";
            default -> "ADJUSTMENT";
        };
    }
}
