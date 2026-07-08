package com.economato.inventory.application.dto.stock.request;

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
public class AlertsConfigRequestDTO {

    @NotNull @Min(1) private Integer alertThresholdOkDays;
    @NotNull @Min(1) private Integer alertThresholdLowDays;
    @NotNull @Min(1) private Integer alertThresholdMediumDays;
    @NotNull @Min(1) private Integer alertThresholdHighDays;
    @NotNull @Min(1) private Integer expirationCriticalDays;
    @NotNull @Min(1) private Integer expirationHighDays;
    @NotNull @Min(1) private Integer expirationMediumDays;
    @NotNull @Min(1) private Integer forecastHorizonDays;
    @NotNull @Min(1) private Integer forecastHistoryWeeks;
}
