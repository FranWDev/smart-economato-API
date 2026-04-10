package com.economato.inventory.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvancedConfigResponseDTO {
    private long outboxProcessingIntervalMs;
    private int outboxBatchSize;
    private int outboxMaxConsecutiveFailures;
    private int kafkaSendTimeoutSeconds;
}
