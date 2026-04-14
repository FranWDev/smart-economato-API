package com.economato.inventory.application.dto.mcp;

import java.math.BigDecimal;

public record McpPlanSlotRequest(
        Long planId,
        Integer recipeId,
        BigDecimal quantity,
        Integer dayOfWeek,
        String startTime,
        String endTime
) {
}
