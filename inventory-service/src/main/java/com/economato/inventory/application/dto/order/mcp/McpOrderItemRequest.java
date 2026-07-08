package com.economato.inventory.application.dto.order.mcp;

import java.math.BigDecimal;

public record McpOrderItemRequest(
        Integer productId,
        BigDecimal quantity
) {
}
