package com.economato.inventory.application.dto.shared.request;

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
public class AdvancedConfigRequestDTO {

    @NotNull @Min(1000) @Max(60000)
    private Long outboxProcessingIntervalMs;

    @NotNull @Min(1) @Max(500)
    private Integer outboxBatchSize;

    @NotNull @Min(1) @Max(50)
    private Integer outboxMaxConsecutiveFailures;

    @NotNull @Min(1) @Max(60)
    private Integer kafkaSendTimeoutSeconds;
}
