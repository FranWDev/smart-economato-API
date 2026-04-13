package com.economato.inventory.application.dto.mcp;

import java.util.List;

public record McpCreateOrderRequest(
        Integer supplierId,
        List<McpOrderItemRequest> items
) {
}
