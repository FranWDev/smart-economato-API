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
public class McpOrderDto {
    private Integer id;
    private String status;
    private BigDecimal totalAmount;
    private int itemCount;
    private String supplierName;
    private String orderDate;
}
