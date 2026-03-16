package com.economato.inventory.application.dto.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpSearchResultDto {
    private List<McpProductDto> products;
    private List<McpRecipeDto> recipes;
}
