package com.economato.inventory.application.dto.stock.mcp;

import java.math.BigDecimal;

public record McpAdjustStockRequest(
        Integer productId,
        BigDecimal quantityDelta,
        String reason
) {
}
