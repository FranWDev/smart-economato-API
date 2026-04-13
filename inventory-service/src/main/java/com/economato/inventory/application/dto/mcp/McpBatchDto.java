package com.economato.inventory.application.dto.mcp;

import java.math.BigDecimal;
import java.time.LocalDate;

public record McpBatchDto(
        Long id,
        LocalDate expirationDate,
        BigDecimal remainingQuantity,
        boolean depleted,
        int daysUntilExpiry
) {
}
