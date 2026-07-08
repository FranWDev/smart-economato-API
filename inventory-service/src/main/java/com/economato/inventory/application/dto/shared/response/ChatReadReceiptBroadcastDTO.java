package com.economato.inventory.application.dto.shared.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatReadReceiptBroadcastDTO {
    private Long incidentId;
    private Integer userId;
    private String userName;
    private Long lastReadMessageId;
    private LocalDateTime readAt;
}
