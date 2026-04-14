package com.economato.inventory.application.dto.mcp;

import java.util.List;

public record McpMenuDayDto(
        Integer dayOfWeek,
        List<McpMenuRecipeDto> recipes
) {
}
