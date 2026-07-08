package com.economato.inventory.application.dto.stock.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertsConfigResponseDTO {
    private int alertThresholdOkDays;
    private int alertThresholdLowDays;
    private int alertThresholdMediumDays;
    private int alertThresholdHighDays;
    private int expirationCriticalDays;
    private int expirationHighDays;
    private int expirationMediumDays;
    private int forecastHorizonDays;
    private int forecastHistoryWeeks;
}
