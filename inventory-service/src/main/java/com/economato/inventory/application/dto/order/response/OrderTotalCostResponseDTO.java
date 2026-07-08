package com.economato.inventory.application.dto.order.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Costo total global de todas las ordenes")
public class OrderTotalCostResponseDTO {

    @Schema(description = "Costo total acumulado de todas las ordenes", example = "1250.80")
    private BigDecimal totalCost;

    @Schema(description = "Cantidad total de ordenes registradas", example = "42")
    private long totalOrders;
}