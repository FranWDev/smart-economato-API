package com.economato.inventory.application.dto.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpProductDto {
    private Integer id;
    private String name;
    private String code;
    private BigDecimal stock;
    private String unit;
    private BigDecimal price;
    private String type;
}
