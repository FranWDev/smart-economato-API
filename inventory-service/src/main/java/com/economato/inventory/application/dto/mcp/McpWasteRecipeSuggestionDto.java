package com.economato.inventory.application.dto.mcp;

import java.math.BigDecimal;

public record McpWasteRecipeSuggestionDto(
        Integer recipeId,
        String recipeName,
        BigDecimal quantityConsumed,
        boolean otherIngredientsSufficient
) {
}
