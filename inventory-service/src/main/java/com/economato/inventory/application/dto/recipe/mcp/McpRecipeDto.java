package com.economato.inventory.application.dto.recipe.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpRecipeDto {
    private Integer id;
    private String name;
    private String code;
    private BigDecimal cost;
    private int allergenCount;
    private String description;
    private String preparation;
}
