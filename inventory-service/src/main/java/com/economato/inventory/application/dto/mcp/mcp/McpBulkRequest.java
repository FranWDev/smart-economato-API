package com.economato.inventory.application.dto.mcp.mcp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpBulkRequest {
    private List<Integer> ids;
    private List<String> codes;
}
