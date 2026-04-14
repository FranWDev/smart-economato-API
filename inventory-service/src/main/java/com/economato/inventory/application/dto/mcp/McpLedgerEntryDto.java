package com.economato.inventory.application.dto.mcp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record McpLedgerEntryDto(
        Long id,
        String movementType,
        BigDecimal quantityDelta,
        BigDecimal resultingStock,
        String description,
        LocalDateTime timestamp,
        String userName
) {
}
