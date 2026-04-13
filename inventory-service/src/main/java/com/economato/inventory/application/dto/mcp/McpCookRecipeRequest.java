package com.economato.inventory.application.dto.mcp;

import java.math.BigDecimal;

public record McpCookRecipeRequest(
        Integer recipeId,
        BigDecimal quantity
) {
}
