package com.economato.inventory.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para el reporte de trazabilidad hacia adelante")
public class ForwardTraceabilityDTO {

    @Schema(description = "Nombre del proveedor")
    private String supplierName;

    @Schema(description = "Lista de nombres de productos analizados")
    private List<String> productNames;

    @Schema(description = "Fecha inicial del rango")
    private LocalDateTime fromDate;

    @Schema(description = "Fecha final del rango")
    private LocalDateTime toDate;

    @Schema(description = "Pedidos (órdenes de compra) afectados")
    private List<OrderResponseDTO> affectedOrders;

    @Schema(description = "Entradas/Salidas en el libro mayor de stock")
    private List<StockLedgerResponseDTO> ledgerEntries;

    @Schema(description = "Cocinados (producción) afectados que usaron estos productos")
    private List<RecipeCookingAuditResponseDTO> affectedCookings;

    @Schema(description = "Lotes afectados en el rango de fechas")
    private List<ProductBatchResponseDTO> affectedBatches;
}
