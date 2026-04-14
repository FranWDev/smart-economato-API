package com.economato.inventory.application.dto.mcp;

public record McpIncidentRequest(
        String title,
        String description,
        String severity
) {
}
