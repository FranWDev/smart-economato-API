package com.economato.inventory.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KitchenReportResponseDTO {
    private String reportPeriod; // DAILY, WEEKLY, etc.
    private Integer totalCookingSessions;
    private BigDecimal totalPortionsCooked;
    private Integer distinctRecipesCooked;
    private Integer distinctUsersCooking;
    private Integer distinctProductsUsed;
    
    private BigDecimal totalEstimatedCost;
    private BigDecimal totalWasteCost;
    private BigDecimal totalExpiredWasteCost; // Pérdidas por retirada de stock caducado (MERMA del ledger)
    private BigDecimal totalSales;
    private BigDecimal grossProfit; // Sales - Net Cost
    private BigDecimal netProfit;   // Sales - Gross Cost
    
    private List<RecipeStatDTO> topRecipes;

    private List<UserStatDTO> topUsers;
    private List<ProductStatDTO> topProducts;
}
