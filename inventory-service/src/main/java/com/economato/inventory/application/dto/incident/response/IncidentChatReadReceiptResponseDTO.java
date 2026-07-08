package com.economato.inventory.application.dto.incident.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentChatReadReceiptResponseDTO {
    private Integer userId;
    private String userName;
    private Long lastReadMessageId;
    private LocalDateTime readAt;
}
