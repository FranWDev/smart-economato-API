package com.economato.inventory.application.dto.mcp;

import java.math.BigDecimal;

public record McpComponentDto(
        Integer productId,
        String productName,
        BigDecimal quantityPerPortion,
        String unit,
        BigDecimal currentStock,
        boolean sufficient
) {
}
