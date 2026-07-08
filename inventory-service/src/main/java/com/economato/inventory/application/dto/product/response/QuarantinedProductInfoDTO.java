package com.economato.inventory.application.dto.product.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Informacion del lote en cuarentena para un producto")
public class QuarantinedProductInfoDTO {

    @Schema(description = "ID del lote", example = "42")
    private Long batchId;

    @Schema(description = "Codigo de lote", example = "LOT-2026-001")
    private String batchCode;

    @Schema(description = "Fecha de caducidad")
    private LocalDate expirationDate;

    @Schema(description = "Cantidad inicial del lote", example = "20.000")
    private BigDecimal initialQuantity;

    @Schema(description = "Cantidad restante del lote", example = "8.500")
    private BigDecimal remainingQuantity;

    @Schema(description = "Fecha de recepcion")
    private LocalDateTime receivedAt;

    @Schema(description = "Indica si el lote esta agotado", example = "false")
    private Boolean depleted;

    @Schema(description = "Hash de ledger legado para compatibilidad transitoria")
    private String ledgerHash;
}
