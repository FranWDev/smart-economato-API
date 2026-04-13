package com.economato.inventory.application.dto.mcp;

import java.math.BigDecimal;

public record McpReorderSuggestionDto(
        Integer productId,
        String productName,
        BigDecimal currentStock,
        BigDecimal projectedConsumption14d,
        BigDecimal pendingInOrders,
        BigDecimal suggestedQuantity,
        String suggestedSupplier,
        String urgency
) {
}
