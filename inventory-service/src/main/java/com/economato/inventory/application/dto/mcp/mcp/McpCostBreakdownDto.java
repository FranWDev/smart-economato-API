package com.economato.inventory.application.dto.mcp.mcp;

import java.math.BigDecimal;
import java.util.Map;

public record McpCostBreakdownDto(
        BigDecimal totalCost,
        Map<String, BigDecimal> costByRecipe,
        Map<String, BigDecimal> costByProduct,
        BigDecimal dailyAverage
) {
}
