package com.economato.inventory.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionsConfigResponseDTO {
    private boolean predictionRefreshEnabled;
    private int predictionRefreshIntervalHours;
    private int predictionHistoryDays;
    private int predictionBatchSize;
}
