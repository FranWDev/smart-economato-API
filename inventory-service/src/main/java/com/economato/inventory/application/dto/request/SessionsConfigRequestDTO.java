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
public class SessionsConfigRequestDTO {

    @NotNull @Min(10) @Max(3600)
    private Long staleSessionTimeoutSeconds;
}
