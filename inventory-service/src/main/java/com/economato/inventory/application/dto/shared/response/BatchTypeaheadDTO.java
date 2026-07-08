package com.economato.inventory.application.dto.shared.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Sugerencia de lote para busqueda incremental")
public class BatchTypeaheadDTO {

    @Schema(description = "ID del lote", example = "42")
    private Long id;

    @Schema(description = "Codigo de lote", example = "LOT-2026-001")
    private String batchCode;

    @Schema(description = "ID del producto", example = "7")
    private Integer productId;

    @Schema(description = "Nombre del producto", example = "Harina")
    private String productName;

    @Schema(description = "Fecha de caducidad", example = "2026-11-30")
    private LocalDate expirationDate;

    @Schema(description = "Cantidad restante", example = "12.500")
    private BigDecimal remainingQuantity;
}
