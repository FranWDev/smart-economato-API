package com.economato.inventory.application.dto.mcp.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpSystemContextDto {
    private long totalProducts;
    private long pendingOrdersCount;
    private long activeAlertsCount;
    private long totalRecipes;
}
