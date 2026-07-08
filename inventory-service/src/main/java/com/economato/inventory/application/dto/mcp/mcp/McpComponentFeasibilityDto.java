package com.economato.inventory.application.dto.mcp.mcp;

import java.math.BigDecimal;

public record McpComponentFeasibilityDto(
        Integer productId,
        String productName,
        BigDecimal required,
        BigDecimal available,
        BigDecimal deficit
) {
}
