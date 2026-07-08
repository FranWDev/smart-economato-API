package com.economato.inventory.application.dto.shared.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityConfigResponseDTO {
    private long jwtExpirationMs;
    private int minPasswordLength;
    private int maxEscalationMinutes;
}
