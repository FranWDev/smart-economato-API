package com.economato.inventory.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyPlanStockRequirementDTO {
    private Integer productId;
    private String productName;
    private BigDecimal requiredQuantity;
    private BigDecimal grossRequiredQuantity;
    private BigDecimal availabilityPercentage;

    private BigDecimal availableStock;
    private BigDecimal reservedByOtherPlans;
    private boolean sufficient;
}
