package com.economato.inventory.application.dto.order.mcp;

import java.util.List;

public record McpCreateOrderRequest(
        Integer supplierId,
        List<McpOrderItemRequest> items
) {
}
