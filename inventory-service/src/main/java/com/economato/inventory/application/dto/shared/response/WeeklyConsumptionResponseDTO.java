package com.economato.inventory.application.dto.shared.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeeklyConsumptionResponseDTO {
    private Integer productId;
    private String productName;
    private String unit;
    /**
     * Lista de consumo semanal ordenada de más antigua a más reciente.
     * Cada posición = 1 semana. Semanas sin consumo = 0.
     */
    private List<BigDecimal> weeklyConsumption;
    /** Número de semanas de historial (normalmente 12). */
    private int weeksOfHistory;
}
