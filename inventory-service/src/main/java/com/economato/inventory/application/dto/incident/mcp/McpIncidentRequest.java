package com.economato.inventory.application.dto.incident.mcp;

public record McpIncidentRequest(
        String title,
        String description,
        String severity
) {
}
