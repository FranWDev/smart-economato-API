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

import com.economato.inventory.annotation.OrderAuditable;
import com.economato.inventory.dto.event.OrderAuditEvent;
import com.economato.inventory.dto.request.OrderReceptionRequestDTO;
import com.economato.inventory.kafka.producer.AuditEventProducer;
import com.economato.inventory.model.Order;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.OrderRepository;
import com.economato.inventory.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

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

        OrderReceptionRequestDTO receptionDto = null;
        Integer orderId = null;
        @SuppressWarnings("unused")
        String newStatus = null;

        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof OrderReceptionRequestDTO) {
                receptionDto = (OrderReceptionRequestDTO) arg;
                orderId = receptionDto.getOrderId();
                newStatus = receptionDto.getStatus();
            } else if (arg instanceof Integer) {
                orderId = (Integer) arg;
            } else if (arg instanceof String && orderId != null) {

                newStatus = (String) arg;
            }
        }

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

            StringBuilder details = new StringBuilder();
            details.append(auditable.action());

            if (previousState != null && !previousState.equals("null")) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> prevMap = objectMapper.readValue(previousState, Map.class);
                    @SuppressWarnings("unchecked")
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

            String newStateStr = buildOrderState(orderAfter);

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

            auditEventProducer.publishOrderAudit(event);

            log.info("Evento de auditoría de orden publicado: orden={}, acción={}",
                    orderAfter.getId(), auditable.action());

        } catch (Exception e) {

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
