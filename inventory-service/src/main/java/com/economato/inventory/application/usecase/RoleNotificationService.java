package com.economato.inventory.application.usecase;

import com.economato.inventory.domain.model.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Send notification to all users with a specific role via WebSocket.
     */
    public void sendNotificationToRole(Role role, String title, String message) {
        sendNotificationToRole(role, new RoleNotificationMessage(title, message));
    }

    /**
     * Send pre-built notification message to a specific role.
     */
    public void sendNotificationToRole(Role role, RoleNotificationMessage notification) {
        try {
            String destination = "/topic/roles/" + role.name();
            
            log.info("Sending notification to role {}: title={}, timestamp={}", 
                    role.name(), notification.getTitle(), notification.getTimestamp());
            
            messagingTemplate.convertAndSend(destination, notification);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification to role: {}", role.name(), e);
        }
    }

    /**
     * Send notification to a specific user.
     */
    public void sendNotificationToUser(String username, String title, String message) {
        try {
            RoleNotificationMessage notification = new RoleNotificationMessage(title, message);
            log.info("Sending notification to user {}: title={}, timestamp={}", 
                    username, title, notification.getTimestamp());
            
            messagingTemplate.convertAndSendToUser(username, "/queue/notifications", notification);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification to user: {}", username, e);
        }
    }
}
