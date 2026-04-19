package com.economato.inventory.application.mapper;

import com.economato.inventory.application.dto.response.KitchenReportResponseDTO;
import com.economato.inventory.application.dto.response.ProductStatDTO;
import com.economato.inventory.application.dto.response.RecipeStatDTO;
import com.economato.inventory.application.dto.response.UserStatDTO;
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
                .totalSales(totalSales)
                .grossProfit(grossProfit)
                .netProfit(netProfit)
                .topRecipes(topRecipes)
                .topUsers(topUsers)
                .topProducts(topProducts)
                .build();

    }
}
