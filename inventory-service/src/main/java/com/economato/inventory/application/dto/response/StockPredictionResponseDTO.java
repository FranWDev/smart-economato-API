package com.economato.inventory.application.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockPredictionResponseDTO {
    private Integer productId;
    private String productName;
    private BigDecimal projectedConsumption;
    private String projectedConsumptionUnit;
    private BigDecimal currentStock;
    private AlertType alertType;
    private LocalDateTime updatedAt;
}
