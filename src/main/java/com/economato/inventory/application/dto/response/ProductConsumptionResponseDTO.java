package com.economato.inventory.application.dto.response;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductConsumptionResponseDTO {
    private Integer productId;
    private String productName;
    private BigDecimal totalConsumed;
    private String unit;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
