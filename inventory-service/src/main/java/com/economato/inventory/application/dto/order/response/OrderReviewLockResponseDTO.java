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
public class OrderReviewLockResponseDTO {
    private Integer orderId;
    private boolean locked;
    private Integer lockedByUserId;
    private String lockedByUsername;
    private String lockedByDisplayName;
    private LocalDateTime acquiredAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime expiresAt;
    private boolean currentUserOwner;
    private boolean currentUserAdmin;
}
