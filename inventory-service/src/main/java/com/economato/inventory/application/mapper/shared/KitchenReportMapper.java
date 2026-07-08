package com.economato.inventory.application.mapper.shared;

import com.economato.inventory.application.dto.shared.response.KitchenReportResponseDTO;
import com.economato.inventory.application.dto.product.response.ProductStatDTO;
import com.economato.inventory.application.dto.recipe.response.RecipeStatDTO;
import com.economato.inventory.application.dto.user.response.UserStatDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class KitchenReportMapper {

    public KitchenReportResponseDTO toReport(
            String reportPeriod,
            int totalSessions,
            BigDecimal totalPortions,
            int distinctRecipes,
            int distinctUsers,
            int distinctProducts,
            BigDecimal totalCost,
            BigDecimal totalWasteCost,
            BigDecimal totalExpiredWasteCost,
            BigDecimal totalSales,
            BigDecimal grossProfit,
            BigDecimal netProfit,
            List<RecipeStatDTO> topRecipes,
            List<UserStatDTO> topUsers,
            List<ProductStatDTO> topProducts) {

        return KitchenReportResponseDTO.builder()
                .reportPeriod(reportPeriod)
                .totalCookingSessions(totalSessions)
                .totalPortionsCooked(totalPortions)
                .distinctRecipesCooked(distinctRecipes)
                .distinctUsersCooking(distinctUsers)
                .distinctProductsUsed(distinctProducts)
                .totalEstimatedCost(totalCost)
                .totalWasteCost(totalWasteCost)
                .totalExpiredWasteCost(totalExpiredWasteCost)
                .totalSales(totalSales)
                .grossProfit(grossProfit)
                .netProfit(netProfit)
                .topRecipes(topRecipes)
                .topUsers(topUsers)
                .topProducts(topProducts)
                .build();

    }
}
