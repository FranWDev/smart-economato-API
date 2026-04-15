package com.economato.inventory.application.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos de un lote individual dentro de la recepción de un producto")
public class LotReceptionRequestDTO {

    @NotNull(message = "{validation.lotReceptionRequestDTO.quantity.notNull}")
    @Positive(message = "{validation.lotReceptionRequestDTO.quantity.positive}")
    @Schema(description = "Cantidad del lote", example = "20.0")
    private BigDecimal quantity;

    @NotNull(message = "{validation.lotReceptionRequestDTO.expirationDate.notNull}")
    @FutureOrPresent(message = "{validation.lotReceptionRequestDTO.expirationDate.futureOrPresent}")
    @Schema(description = "Fecha de caducidad del lote", example = "2026-12-31")
    private LocalDate expirationDate;

    @Size(max = 100, message = "{validation.lotReceptionRequestDTO.batchCode.size}")
    @Schema(description = "Codigo identificativo del lote", example = "LOT-2026-001")
    private String batchCode;
}
