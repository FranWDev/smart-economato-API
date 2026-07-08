package com.economato.inventory.application.dto.weeklyplan.mcp;

import java.math.BigDecimal;
import java.time.LocalTime;

public record McpSlotDto(
        Long slotId,
        Integer recipeId,
        String recipeName,
        BigDecimal quantity,
        Integer dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        String status
) {
}
