package com.economato.inventory.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Información de un lote recibido en la recepción de una orden")
public class LotReceptionResponseDTO {

    @Schema(description = "Cantidad del lote recibido", example = "20.0")
    private BigDecimal quantity;

    @Schema(description = "Fecha de caducidad del lote", example = "2026-12-31")
    private LocalDate expirationDate;

    @Schema(description = "Codigo identificativo del lote", example = "LOT-2026-001")
    private String batchCode;
}
