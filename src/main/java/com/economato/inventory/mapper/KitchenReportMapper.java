package com.economato.inventory.mapper;

import com.economato.inventory.dto.response.KitchenReportResponseDTO;
import com.economato.inventory.dto.response.ProductStatDTO;
import com.economato.inventory.dto.response.RecipeStatDTO;
import com.economato.inventory.dto.response.UserStatDTO;
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
                .topRecipes(topRecipes)
                .topUsers(topUsers)
                .topProducts(topProducts)
                .build();
    }
}
