package com.economato.inventory.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyPlanStockRequirementDTO {
    private Integer productId;
    private String productName;
    private String unit;
    private BigDecimal requiredQuantity;
    private BigDecimal grossRequiredQuantity;
    private BigDecimal availabilityPercentage;

    private BigDecimal availableStock;
    private BigDecimal grossAvailableStock;
    private BigDecimal reservedByOtherPlans;
    private BigDecimal grossReservedByOtherPlans;

    private BigDecimal expiredStock;
    private BigDecimal grossExpiredStock;

    private BigDecimal expiringBeforePlanStock;
    private LocalDate nearestExpirationDate;
    private boolean expirationRisk;

    private BigDecimal lotQuantity;

    private boolean sufficient;
}
