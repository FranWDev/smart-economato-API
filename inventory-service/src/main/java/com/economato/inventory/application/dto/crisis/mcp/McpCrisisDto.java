package com.economato.inventory.application.dto.crisis.mcp;

import java.time.LocalDateTime;
import java.util.List;

public record McpCrisisDto(
        Long id,
        String crisisCode,
        String reason,
        String supplierName,
        String status,
        LocalDateTime dateFrom,
        LocalDateTime dateTo,
        List<McpCrisisProductDto> affectedProducts
) {
}
