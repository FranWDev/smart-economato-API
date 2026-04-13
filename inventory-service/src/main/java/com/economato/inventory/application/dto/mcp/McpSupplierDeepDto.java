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
public class McpSupplierDeepDto {
    private Integer id;
    private String name;
    private String phone;
    private String email;
    private List<McpProductDto> products;
    private int recentOrderCount;
    private boolean hasCrisis;
}
