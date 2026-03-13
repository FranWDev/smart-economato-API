package com.economato.inventory.application.usecase;

import com.economato.inventory.domain.model.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RoleNotificationServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private RoleNotificationService roleNotificationService;

    @Test
    void sendNotificationToRole_ShouldSendMessageToCorrectTopic() {
        // Arrange
        Role role = Role.ADMIN;
        String title = "Test Title";
        String message = "Test Message";
        String expectedTopic = "/topic/roles/ADMIN";

        // Act
        roleNotificationService.sendNotificationToRole(role, title, message);

        // Assert
        verify(messagingTemplate).convertAndSend(eq(expectedTopic), any(RoleNotificationMessage.class));
    }

    @Test
    void sendNotificationToUser_ShouldSendMessageToCorrectUserQueue() {
        // Arrange
        String username = "testuser";
        String title = "User Title";
        String message = "User Message";
        String expectedQueue = "/queue/notifications";

        // Act
        roleNotificationService.sendNotificationToUser(username, title, message);

        // Assert
        verify(messagingTemplate).convertAndSendToUser(eq(username), eq(expectedQueue), any(RoleNotificationMessage.class));
    }
}
