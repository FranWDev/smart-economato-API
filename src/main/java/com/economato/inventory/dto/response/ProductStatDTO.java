package com.economato.inventory.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductStatDTO {
    private Integer productId;
    private String productName;
    private String unit;
    private BigDecimal totalQuantityUsed;
    private BigDecimal estimatedCost; // Computed dynamically
}
