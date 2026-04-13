package com.economato.inventory.application.dto.mcp;

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
public class McpProductDeepDto {
    private Integer id;
    private String name;
    private String code;
    private BigDecimal stock;
    private String unit;
    private BigDecimal price;
    private String supplierName;
    private String alertLevel;
    private Integer daysToNearestExpiry;
    private McpPredictionDto prediction;
    private List<BigDecimal> dailyForecast;
    private List<BigDecimal> weeklyConsumption;
    private List<McpBatchDto> batches;
}
