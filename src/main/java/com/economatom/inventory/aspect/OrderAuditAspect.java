package com.economatom.inventory.aspect;

import com.economatom.inventory.annotation.OrderAuditable;
import com.economatom.inventory.dto.event.OrderAuditEvent;
import com.economatom.inventory.dto.request.OrderReceptionRequestDTO;
import com.economatom.inventory.kafka.producer.AuditEventProducer;
import com.economatom.inventory.model.Order;
import com.economatom.inventory.model.User;
import com.economatom.inventory.repository.OrderRepository;
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

/**
 * Aspecto para auditoría de órdenes.
 * Publica eventos en Kafka de forma asíncrona y no bloqueante.
 */
@Aspect
@Component
@Profile("!test")
public class OrderAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(OrderAuditAspect.class);
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final AuditEventProducer auditEventProducer;

    public OrderAuditAspect(
            OrderRepository orderRepository,
            UserRepository userRepository,
            AuditEventProducer auditEventProducer) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.auditEventProducer = auditEventProducer;
    }

    @Around("@annotation(auditable)")
    public Object logOrderAction(ProceedingJoinPoint joinPoint, OrderAuditable auditable) throws Throwable {
        // Extraer OrderReceptionRequestDTO o ID del método
        OrderReceptionRequestDTO receptionDto = null;
        Integer orderId = null;
        String newStatus = null;
        
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof OrderReceptionRequestDTO) {
                receptionDto = (OrderReceptionRequestDTO) arg;
                orderId = receptionDto.getOrderId();
                newStatus = receptionDto.getStatus();
            } else if (arg instanceof Integer) {
                orderId = (Integer) arg;
            } else if (arg instanceof String && orderId != null) {
                // Para updateStatus(orderId, status)
                newStatus = (String) arg;
            }
        }

        // Capturar el estado ANTES del cambio
        String previousState = null;
        if (orderId != null) {
            Order orderBefore = orderRepository.findById(orderId).orElse(null);
            if (orderBefore != null) {
                previousState = buildOrderState(orderBefore);
            }
        }

        Object result = joinPoint.proceed();

        try {
            if (orderId == null) {
                log.debug("No se pudo determinar el ID de la orden para auditoría");
                return result;
            }

            Order orderAfter = orderRepository.findById(orderId).orElse(null);
            if (orderAfter == null) {
                log.warn("Orden no encontrada para auditoría: {}", orderId);
                return result;
            }

            User user = getCurrentUser();

            // Construir detalles de la auditoría
            StringBuilder details = new StringBuilder();
            details.append(auditable.action());
            
            if (previousState != null && !previousState.equals("null")) {
                try {
                    Map<String, Object> prevMap = objectMapper.readValue(previousState, Map.class);
                    Map<String, Object> newMap = objectMapper.readValue(buildOrderState(orderAfter), Map.class);
                    
                    String prevStatus = (String) prevMap.get("estado");
                    String currStatus = (String) newMap.get("estado");
                    
                    if (prevStatus != null && !prevStatus.equals(currStatus)) {
                        details.append(" - Estado cambiado de ").append(prevStatus).append(" a ").append(currStatus);
                    }
                } catch (Exception e) {
                    log.debug("No se pudo extraer cambio de estado: {}", e.getMessage());
                }
            }

            // Construir estado posterior
            String newStateStr = buildOrderState(orderAfter);

            // Construir evento de auditoría
            OrderAuditEvent event = OrderAuditEvent.builder()
                .orderId(orderAfter.getId())
                .userId(user != null ? user.getId() : null)
                .userName(user != null ? user.getName() : "Sistema")
                .action(auditable.action())
                .details(details.toString())
                .previousState(previousState)
                .newState(newStateStr)
                .auditDate(LocalDateTime.now())
                .build();

            // Publicar evento en Kafka de forma asíncrona
            auditEventProducer.publishOrderAudit(event);

            log.info("Evento de auditoría de orden publicado: orden={}, acción={}", 
                orderAfter.getId(), auditable.action());

        } catch (Exception e) {
            // No propagar excepción para no afectar la operación principal
            log.error("Error al publicar evento de auditoría de orden: {}", e.getMessage(), e);
        }

        return result;
    }

    private String buildOrderState(Order order) {
        try {
            Map<String, Object> state = new HashMap<>();
            state.put("id", order.getId());
            state.put("estado", order.getStatus());
            state.put("fechaOrden", order.getOrderDate());
            state.put("usuarioId", order.getUsers() != null ? order.getUsers().getId() : null);
            state.put("numeroDetalles", order.getDetails() != null ? order.getDetails().size() : 0);
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            log.error("Error al serializar estado de la orden: {}", e.getMessage());
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
