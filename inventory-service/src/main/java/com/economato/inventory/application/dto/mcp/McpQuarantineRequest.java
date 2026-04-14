package com.economato.inventory.application.dto.mcp;

public record McpQuarantineRequest(
        Long batchId,
        String reason
) {
}
