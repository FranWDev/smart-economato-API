package com.economato.inventory.application.dto.notification.response;

import com.economato.inventory.domain.model.notification.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponseDTO {
    private Long id;
    private NotificationType type;
    private String title;
    private String message;
    private Long referenceId;
    private boolean isRead;
    private String senderName;
    private String groupId;
    private LocalDateTime createdAt;
}
