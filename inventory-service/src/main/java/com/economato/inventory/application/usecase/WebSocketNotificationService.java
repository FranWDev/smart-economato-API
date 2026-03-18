package com.economato.inventory.application.usecase;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.economato.inventory.infrastructure.CircuitBreakerClosedEvent;
import com.economato.inventory.infrastructure.CircuitBreakerOpenEvent;
import com.economato.inventory.infrastructure.WebSocketConnectedEvent;

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

    /**
     * Maneja eventos de circuit breaker OPEN y envía alertas al frontend a través de WebSocket.
     * El frontend debe traducir el código de alerta a un mensaje legible para el usuario y determinar si es un fallo parcial o crítico.
     */
    @EventListener
    public void handleCircuitBreakerOpen(CircuitBreakerOpenEvent event) {
        String instanceName = event.getInstanceName();
        log.info("Received CircuitBreakerOpenEvent for instance: {}", instanceName);

        if ("db".equals(instanceName)) {
            sendCircuitBreakerAlert(AlertCode.DB_FAILURE);
        } else if ("redis".equals(instanceName)) {
            sendCircuitBreakerAlert(AlertCode.REDIS_FAILURE);
        } else if ("kafka".equals(instanceName)) {
            sendCircuitBreakerAlert(AlertCode.KAFKA_FAILURE);
        } else if ("replica".equals(instanceName)) {
            sendCircuitBreakerAlert(AlertCode.REPLICA_FAILURE);
        }
    }

    /**
     * Maneja eventos de circuit breaker CLOSED (recuperación) y envía alertas al frontend a través de WebSocket.
     */
    @EventListener
    public void handleCircuitBreakerClosed(CircuitBreakerClosedEvent event) {
        String instanceName = event.getInstanceName();
        log.info("Received CircuitBreakerClosedEvent for instance: {}", instanceName);

        if ("db".equals(instanceName)) {
            sendCircuitBreakerAlert(AlertCode.DB_RECOVERED);
        } else if ("redis".equals(instanceName)) {
            sendCircuitBreakerAlert(AlertCode.REDIS_RECOVERED);
        } else if ("kafka".equals(instanceName)) {
            sendCircuitBreakerAlert(AlertCode.KAFKA_RECOVERED);
        } else if ("replica".equals(instanceName)) {
            sendCircuitBreakerAlert(AlertCode.REPLICA_RECOVERED);
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
        } catch (Exception e) {
            log.error("Failed to send WebSocket alert for code: {}", alertCode.getCode(), e);
        }
    }

    public void sendCircuitBreakerAlertToUser(String username, AlertCode alertCode) {
        try {
            AlertMessage message = new AlertMessage(alertCode.getCode(), alertCode.getDescription());
            log.info("Sending System Alert to user {} via WebSocket: code={}, timestamp={}", 
                    username, alertCode.getCode(), message.getTimestamp());
            messagingTemplate.convertAndSendToUser(username, "/queue/alerts", message);
        } catch (Exception e) {
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
        } catch (Exception e) {
            log.error("Error checking circuit breakers for user: {}", username, e);
        }
    }
}
