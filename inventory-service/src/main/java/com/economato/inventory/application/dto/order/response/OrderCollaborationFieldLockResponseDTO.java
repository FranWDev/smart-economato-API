package com.economato.inventory.application.dto.order.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCollaborationFieldLockResponseDTO {
    private String fieldPath;
    private Integer lockedByUserId;
    private String lockedByUsername;
    private String lockedByDisplayName;
    private LocalDateTime lockedAt;
    private LocalDateTime expiresAt;
}
