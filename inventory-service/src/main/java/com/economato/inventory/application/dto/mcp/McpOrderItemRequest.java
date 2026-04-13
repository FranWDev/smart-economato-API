package com.economato.inventory.application.dto.mcp;

import java.math.BigDecimal;

public record McpOrderItemRequest(
        Integer productId,
        BigDecimal quantity
) {
}
