package com.economato.inventory.application.dto.stock.mcp;

import java.math.BigDecimal;

public record McpAlertDto(
        Integer productId,
        String productName,
        String severity,
        String resolution,
        BigDecimal currentStock,
        BigDecimal projectedConsumption,
        BigDecimal pendingQuantity
) {
}
