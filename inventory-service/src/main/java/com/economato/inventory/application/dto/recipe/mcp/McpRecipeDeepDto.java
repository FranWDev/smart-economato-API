package com.economato.inventory.application.dto.recipe.mcp;
import com.economato.inventory.application.dto.mcp.mcp.McpComponentDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpRecipeDeepDto {
    private Integer id;
    private String name;
    private String code;
    private BigDecimal cost;
    private String description;
    private String preparation;
    private List<McpComponentDto> components;
    private List<String> allergens;
    private BigDecimal costPerPortion;
    private int recentCookingCount;
}
