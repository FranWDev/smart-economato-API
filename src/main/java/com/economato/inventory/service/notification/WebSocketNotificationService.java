package com.economato.inventory.service.notification;

import com.economato.inventory.event.CircuitBreakerOpenEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.event.EventListener;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageSource messageSource;

    @EventListener
    public void handleCircuitBreakerOpen(CircuitBreakerOpenEvent event) {
        String instanceName = event.getInstanceName();
        log.info("Received CircuitBreakerOpenEvent for instance: {}", instanceName);

        if ("db".equals(instanceName)) {
            sendCircuitBreakerAlert("alert.circuitbreaker.db.down");
        } else if ("redis".equals(instanceName) || "kafka".equals(instanceName)) {
            sendCircuitBreakerAlert("alert.circuitbreaker.partial.down");
        }
    }

    public void sendCircuitBreakerAlert(String messageKey) {
        try {
            String localizedMsg = messageSource.getMessage(messageKey, null, LocaleContextHolder.getLocale());
            log.info("Sending System Alert via WebSocket: {}", localizedMsg);
            messagingTemplate.convertAndSend("/topic/alerts", localizedMsg);
        } catch (Exception e) {
            log.error("Failed to send WebSocket alert for key: {}", messageKey, e);
        }
    }
}
