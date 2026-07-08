package com.economato.inventory.application.dto.product.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductStatsResponseDTO {
    private long totalProducts;
    private BigDecimal totalInventoryValue;
    private BigDecimal averagePrice;
}
