package com.economato.inventory.application.dto.mcp.mcp;
import com.economato.inventory.application.dto.recipe.mcp.McpMenuRecipeDto;

import java.util.List;

public record McpMenuDayDto(
        Integer dayOfWeek,
        List<McpMenuRecipeDto> recipes
) {
}
