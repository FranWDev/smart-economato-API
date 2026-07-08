package com.economato.inventory.application.dto.shared.mcp;

public record ToolCallInfo(
        String toolName,
        String toolCallId,
        String toolResult
) {
}
