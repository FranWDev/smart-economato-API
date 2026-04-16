package com.economato.inventory.application.dto.mcp;

public record ToolCallInfo(
        String toolName,
        String toolCallId,
        String toolResult
) {
}
