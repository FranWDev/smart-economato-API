package com.economato.inventory.application.dto.request;

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
public class SecurityConfigRequestDTO {

    @NotNull @Min(300000) @Max(604800000)
    private Long jwtExpirationMs;

    @NotNull @Min(4) @Max(128)
    private Integer minPasswordLength;

    @NotNull @Min(1) @Max(10080)
    private Integer maxEscalationMinutes;
}
