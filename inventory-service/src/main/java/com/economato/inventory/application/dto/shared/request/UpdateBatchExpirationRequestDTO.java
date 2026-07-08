package com.economato.inventory.application.dto.shared.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud para actualizar la fecha de caducidad de un lote")
public class UpdateBatchExpirationRequestDTO {

    @NotNull(message = "La nueva fecha de caducidad es obligatoria")
    @FutureOrPresent(message = "La fecha de caducidad debe ser presente o futura")
    @Schema(description = "Nueva fecha de caducidad", example = "2026-12-31")
    private LocalDate expirationDate;

    @Size(max = 500)
    @Schema(description = "Motivo del cambio de caducidad", example = "Corrección de fecha errónea en recepción")
    private String reason;
    
    @Size(max = 100)
    @Schema(description = "Código identificativo del lote", example = "LOT-2026-001")
    private String batchCode;
}
