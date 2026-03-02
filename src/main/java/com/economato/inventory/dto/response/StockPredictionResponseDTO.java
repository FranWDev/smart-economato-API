package com.economato.inventory.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO para representar una predicción de stock en las consultas paginadas.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockPredictionResponseDTO {
    private Integer productId;
    private String productName;
    private BigDecimal projectedConsumption;
    private LocalDateTime updatedAt;
}
