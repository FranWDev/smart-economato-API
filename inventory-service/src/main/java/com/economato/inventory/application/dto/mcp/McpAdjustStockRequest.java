package com.economato.inventory.application.dto.mcp;

import java.math.BigDecimal;

public record McpAdjustStockRequest(
        Integer productId,
        BigDecimal quantityDelta,
        String reason
) {
}
