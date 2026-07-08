package com.economato.inventory.application.dto.weeklyplan.mcp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record McpWeeklyPlanDeepDto(
        Long planId,
        String status,
        LocalDate weekStart,
        LocalDate weekEnd,
        List<McpSlotDto> slots,
        Map<Integer, BigDecimal> totalStockImpact
) {
}
