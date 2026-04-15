package com.economato.inventory.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detalle de lotes afectados durante una crisis")
public class CrisisAffectedBatchDTO {

    @Schema(description = "ID del lote", example = "12")
    private Long batchId;

    @Schema(description = "ID del producto", example = "45")
    private Integer productId;

    @Schema(description = "Nombre del producto", example = "Lechuga")
    private String productName;

    @Schema(description = "Fecha de caducidad del lote", example = "2026-06-30")
    private LocalDate expirationDate;

    @Schema(description = "Codigo del lote", example = "LOT-2026-001")
    private String batchCode;

    @Schema(description = "Stock restante en el lote", example = "8.500")
    private BigDecimal remainingQuantity;

    @Schema(description = "Indica si el lote está caducado", example = "false")
    private boolean expired;

    @Schema(description = "Indica si el lote está agotado o retirado", example = "false")
    private boolean depleted;
}
