package com.economato.inventory.service.notification;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.economato.inventory.event.CircuitBreakerClosedEvent;
import com.economato.inventory.event.CircuitBreakerOpenEvent;
import com.economato.inventory.event.WebSocketConnectedEvent;

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
     * Handle circuit breaker OPEN events (failures).
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
     * Handle circuit breaker CLOSED events (recovery).
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
     * Send alert with error code to frontend via WebSocket.
     * Frontend is responsible for translating the code to user's locale.
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

    /**
     * Send alert to a specific user session.
     * Used when a new WebSocket connection is established to notify about open circuit breakers.
     */
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
     * Handle new WebSocket connections.
     * Check all circuit breakers and notify the user about any that are currently OPEN.
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
