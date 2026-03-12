package com.economato.inventory.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Resultado de filtrado de ordenes con costo total acumulado")
public class OrderFilterResponseDTO {

    @Schema(description = "Ordenes encontradas con los filtros aplicados")
    private List<OrderResponseDTO> orders;

    @Schema(description = "Costo total acumulado de todas las ordenes filtradas", example = "452.75")
    private BigDecimal totalCost;

    @Schema(description = "Cantidad total de ordenes encontradas", example = "8")
    private long totalOrders;
}