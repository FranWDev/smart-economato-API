package com.economato.inventory.application.dto.mcp.mcp;

import java.util.List;

public record McpFeasibilityDto(
        boolean feasible,
        List<McpComponentFeasibilityDto> components
) {
}
