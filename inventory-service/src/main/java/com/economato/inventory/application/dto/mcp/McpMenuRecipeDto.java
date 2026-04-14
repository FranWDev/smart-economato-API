package com.economato.inventory.application.dto.mcp;

import java.math.BigDecimal;
import java.util.List;

public record McpMenuRecipeDto(
        Integer recipeId,
        String recipeName,
        BigDecimal costPerPortion,
        List<String> allergens
) {
}
