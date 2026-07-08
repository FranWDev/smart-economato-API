package com.economato.inventory.application.dto.notification.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresenceConfigRequestDTO {

    @NotNull
    private Boolean presenceAuditEnabled;

    @NotNull
    private Boolean presenceAutoCleanupEnabled;

    @Min(1)
    @Max(3650)
    private Integer presenceAutoCleanupDays;
}
