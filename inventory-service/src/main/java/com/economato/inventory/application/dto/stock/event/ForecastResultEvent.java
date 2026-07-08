package com.economato.inventory.application.dto.stock.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * DTO para recibir resultados de predicción desde el microservicio Python.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForecastResultEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private Integer productId;
    private BigDecimal projectedConsumption;
    private OffsetDateTime calculatedAt;
    private String modelUsed;
    private BigDecimal confidenceScore;
    private ForecastResultType eventType;
}
