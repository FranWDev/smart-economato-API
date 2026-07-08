package com.economato.inventory.application.dto.mcp.mcp;
import com.economato.inventory.application.dto.recipe.mcp.McpWasteRecipeSuggestionDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record McpWasteRiskDto(
        Long batchId,
        Integer productId,
        String productName,
        LocalDate expirationDate,
        int daysUntilExpiry,
        BigDecimal remainingQuantity,
        List<McpWasteRecipeSuggestionDto> recipeSuggestions
) {
}
