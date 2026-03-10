package com.economato.inventory.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyForecastResponseDTO {
    private Integer productId;
    private String productName;
    private String unit;
    /** Lista de consumo diario proyectado. Posición 0 = mañana, posición 13 = día 14. */
    private List<BigDecimal> dailyForecast;
    /** Horizonte en días (normalmente 14). */
    private int horizonDays;
    private LocalDateTime calculatedAt;
}
