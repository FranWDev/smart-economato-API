package com.economato.inventory.application.dto.mcp.mcp;

import java.math.BigDecimal;
import java.util.List;

public record McpMenuSuggestionDto(
        List<McpMenuDayDto> days,
        BigDecimal totalEstimatedCost
) {
}
