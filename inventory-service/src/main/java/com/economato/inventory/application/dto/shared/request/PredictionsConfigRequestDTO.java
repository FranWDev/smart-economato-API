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
public class PredictionsConfigRequestDTO {

    @NotNull
    private Boolean predictionRefreshEnabled;

    @NotNull @Min(1) @Max(24)
    private Integer predictionRefreshIntervalHours;

    @NotNull @Min(7) @Max(365)
    private Integer predictionHistoryDays;

    @NotNull @Min(1) @Max(100)
    private Integer predictionBatchSize;
}
