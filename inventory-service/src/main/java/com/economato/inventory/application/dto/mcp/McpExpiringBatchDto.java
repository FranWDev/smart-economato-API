package com.economato.inventory.application.dto.mcp;

import java.math.BigDecimal;
import java.time.LocalDate;

public record McpExpiringBatchDto(
        Integer productId,
        String productName,
        Long batchId,
        LocalDate expirationDate,
        BigDecimal remainingQuantity,
        int daysUntilExpiry
) {
}
