package com.economato.inventory.service.notification;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.economato.inventory.event.CircuitBreakerClosedEvent;
import com.economato.inventory.event.CircuitBreakerOpenEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

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
}
