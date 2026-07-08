package com.economato.inventory.application.dto.notification.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresenceConfigResponseDTO {
    private boolean presenceAuditEnabled;
    private boolean presenceAutoCleanupEnabled;
    private Integer presenceAutoCleanupDays;
    private long totalLogCount;
}
