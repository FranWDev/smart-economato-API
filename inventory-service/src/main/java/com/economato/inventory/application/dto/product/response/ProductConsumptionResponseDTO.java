package com.economato.inventory.application.dto.product.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductConsumptionResponseDTO {
    private Integer productId;
    private String productName;
    private List<DailyConsumptionDTO> breakdown;
    private String unit;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DailyConsumptionDTO {
        private LocalDate date;
        private BigDecimal consumed;
    }
}
