package com.economato.inventory.application.dto.crisis.mcp;

import java.math.BigDecimal;

public record McpCrisisProductDto(
        Integer productId,
        String productName,
        BigDecimal quarantinedQuantity
) {
}
