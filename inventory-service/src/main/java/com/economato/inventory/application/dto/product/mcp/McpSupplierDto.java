package com.economato.inventory.application.dto.product.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpSupplierDto {
    private Integer id;
    private String name;
    private String contact;
    private int productCount;
}
