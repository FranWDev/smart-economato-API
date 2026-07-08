package com.economato.inventory.application.dto.mcp.mcp;
import com.economato.inventory.application.dto.product.mcp.McpProductDto;
import com.economato.inventory.application.dto.recipe.mcp.McpRecipeDto;

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
