package com.economato.inventory.application.usecase;

import java.time.LocalDateTime;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.economato.inventory.application.dto.event.RealtimeSyncEvent;
import com.economato.inventory.infrastructure.CircuitBreakerClosedEvent;
import com.economato.inventory.infrastructure.CircuitBreakerOpenEvent;
import com.economato.inventory.infrastructure.WebSocketConnectedEvent;
import com.economato.inventory.infrastructure.WebSocketDisconnectedEvent;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final I18nService i18nService;
    private final UserPresenceService userPresenceService;
    private final OrderReviewLockService orderReviewLockService;

    /**
     * Maneja eventos de circuit breaker OPEN y envía alertas al frontend a través de WebSocket.
     * El frontend debe traducir el código de alerta a un mensaje legible para el usuario y determinar si es un fallo parcial o crítico.
     */
    @EventListener
    public void handleCircuitBreakerOpen(CircuitBreakerOpenEvent event) {
        String instanceName = event.getInstanceName();
        log.info("Received CircuitBreakerOpenEvent for instance: {}", instanceName);

        switch (instanceName) {
            case "db" -> sendCircuitBreakerAlert(AlertCode.DB_FAILURE);
            case "redis" -> sendCircuitBreakerAlert(AlertCode.REDIS_FAILURE);
            case "kafka" -> sendCircuitBreakerAlert(AlertCode.KAFKA_FAILURE);
            case "replica" -> sendCircuitBreakerAlert(AlertCode.REPLICA_FAILURE);
            default -> {
                // no-op
            }
        }
    }

    /**
     * Maneja eventos de circuit breaker CLOSED (recuperación) y envía alertas al frontend a través de WebSocket.
     */
    @EventListener
    public void handleCircuitBreakerClosed(CircuitBreakerClosedEvent event) {
        String instanceName = event.getInstanceName();
        log.info("Received CircuitBreakerClosedEvent for instance: {}", instanceName);

        switch (instanceName) {
            case "db" -> sendCircuitBreakerAlert(AlertCode.DB_RECOVERED);
            case "redis" -> sendCircuitBreakerAlert(AlertCode.REDIS_RECOVERED);
            case "kafka" -> sendCircuitBreakerAlert(AlertCode.KAFKA_RECOVERED);
            case "replica" -> sendCircuitBreakerAlert(AlertCode.REPLICA_RECOVERED);
            default -> {
                // no-op
            }
        }
    }

    /**
     * Envía una alerta de sistema a todos los clientes WebSocket suscritos al tópico de alertas.
     */
    public void sendCircuitBreakerAlert(AlertCode alertCode) {
        try {
            AlertMessage message = new AlertMessage(alertCode.getCode(), alertCode.getDescription());
            log.info("Sending System Alert via WebSocket: code={}, timestamp={}", 
                    alertCode.getCode(), message.getTimestamp());
            messagingTemplate.convertAndSend("/topic/alerts", message);
        } catch (RuntimeException e) {
            log.error("Failed to send WebSocket alert for code: {}", alertCode.getCode(), e);
        }
    }

    public void sendCircuitBreakerAlertToUser(String username, AlertCode alertCode) {
        try {
            AlertMessage message = new AlertMessage(alertCode.getCode(), alertCode.getDescription());
            log.info("Sending System Alert to user {} via WebSocket: code={}, timestamp={}", 
                    username, alertCode.getCode(), message.getTimestamp());
            messagingTemplate.convertAndSendToUser(username, "/queue/alerts", message);
        } catch (RuntimeException e) {
            log.error("Failed to send WebSocket alert to user {} for code: {}", username, alertCode.getCode(), e);
        }
    }

    /**
     * Si se conecta un nuevo cliente WebSocket, revisa el estado de todos los circuit breakers y 
     * envía alertas de cualquier recurso que esté actualmente en estado OPEN.
     */
    @EventListener
    public void handleWebSocketConnected(WebSocketConnectedEvent event) {
        String username = event.getUsername();
        userPresenceService.userConnected(username, event.getSessionId(), null, null, null, null);
        log.debug("Checking open circuit breakers for new WebSocket connection from user: {}", username);
        
        try {
            CircuitBreaker dbCircuitBreaker = circuitBreakerRegistry.circuitBreaker("db");
            if (dbCircuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                log.info("Database circuit breaker is OPEN, notifying user: {}", username);
                sendCircuitBreakerAlertToUser(username, AlertCode.DB_FAILURE);
            }

            CircuitBreaker redisCircuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
            if (redisCircuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                log.info("Redis circuit breaker is OPEN, notifying user: {}", username);
                sendCircuitBreakerAlertToUser(username, AlertCode.REDIS_FAILURE);
            }

            CircuitBreaker kafkaCircuitBreaker = circuitBreakerRegistry.circuitBreaker("kafka");
            if (kafkaCircuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                log.info("Kafka circuit breaker is OPEN, notifying user: {}", username);
                sendCircuitBreakerAlertToUser(username, AlertCode.KAFKA_FAILURE);
            }

            CircuitBreaker replicaCircuitBreaker = circuitBreakerRegistry.circuitBreaker("replica");
            if (replicaCircuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                log.info("Replica circuit breaker is OPEN, notifying user: {}", username);
                sendCircuitBreakerAlertToUser(username, AlertCode.REPLICA_FAILURE);
            }
        } catch (RuntimeException e) {
            log.error("Error checking circuit breakers for user: {}", username, e);
        }
    }

    @EventListener
    public void handleWebSocketDisconnected(WebSocketDisconnectedEvent event) {
        userPresenceService.userDisconnected(event.getUsername(), event.getSessionId());
        orderReviewLockService.releaseLocksForUser(event.getUsername());
    }

    /**
     * Envía una notificación de predicción de stock a todos los administradores.
     */
    public void notifyAdminsStockPrediction(int productCount) {
        try {
            String description = i18nService.getMessage(
                    MessageKey.NOTIFICATION_PREDICTION_TRIGGERED, productCount);

            AlertMessage message = new AlertMessage(
                    AlertCode.STOCK_PREDICTION_TRIGGERED.getCode(), description);

            log.info("Notifying admins via WebSocket: prediction triggered for {} products", productCount);
            
            // Enviamos al tópico general de alertas, pero el frontend debería filtrarlo o 
            // podríamos enviarlo a un tópico específico /topic/admin/notifications si existiera.
            // Para seguir el patrón existente, usamos /topic/alerts.
            messagingTemplate.convertAndSend("/topic/alerts", message);
        } catch (RuntimeException e) {
            log.error("Failed to send WebSocket prediction notification", e);
        }
    }

    /**
     * Emite un evento de sincronización silencioso al topic {@code /topic/sync}.
     * El frontend usa {@code affectedDomains} para invalidar su cache local
     * y re-fetchar los datos afectados, sin mostrar ninguna notificación visual.
     *
     * <p>Este método es invocado exclusivamente por {@code RealtimeSyncAspect}
     * tras la ejecución exitosa de una operación mutante del sistema. Nunca
     * propaga excepciones para no afectar el flujo principal.
     */
    public void broadcastSync(RealtimeSyncEvent event) {
        try {
            if (event.getTimestamp() == null) {
                event.setTimestamp(LocalDateTime.now());
            }
            log.debug("Broadcasting sync event: entityType={}, action={}, domains={}",
                    event.getEntityType(), event.getAction(), event.getAffectedDomains());
            messagingTemplate.convertAndSend("/topic/sync", event);
        } catch (RuntimeException e) {
            log.error("Failed to broadcast sync event for entityType={}: {}",
                    event.getEntityType(), e.getMessage(), e);
        }
    }
}
