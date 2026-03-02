package com.economato.inventory.dto.response;

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
    
    private List<RecipeStatDTO> topRecipes;
    private List<UserStatDTO> topUsers;
    private List<ProductStatDTO> topProducts;
}
