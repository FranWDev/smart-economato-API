package com.economato.inventory.application.dto.mcp.mcp;

public record McpQuarantineRequest(
        Long batchId,
        String reason
) {
}
