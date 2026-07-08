package com.economato.inventory.application.dto.recipe.mcp;

import java.math.BigDecimal;

public record McpCookRecipeRequest(
        Integer recipeId,
        BigDecimal quantity
) {
}
