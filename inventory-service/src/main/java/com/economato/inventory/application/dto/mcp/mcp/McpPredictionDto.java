package com.economato.inventory.application.dto.mcp.mcp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record McpPredictionDto(
        BigDecimal projectedConsumption14d,
        LocalDateTime updatedAt
) {
}
