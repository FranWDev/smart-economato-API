package com.economato.inventory.application.dto.recipe.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeStatsResponseDTO {
    private long totalRecipes;
    private long recipesWithAllergens;
    private long recipesWithoutAllergens;
    private BigDecimal averagePrice;
}
