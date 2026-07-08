package com.economato.inventory.application.dto.crisis.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para activar una crisis de seguridad alimentaria")
public class CrisisActivationRequestDTO {

    @NotNull(message = "El ID del proveedor es obligatorio")
    @Schema(description = "ID del proveedor afectado")
    private Integer supplierId;

    @NotEmpty(message = "Debe haber al menos un producto afectado")
    @Schema(description = "Lista de IDs de los productos afectados")
    private List<Integer> productIds;

    @NotNull(message = "La fecha de inicio es obligatoria")
    @Schema(description = "Fecha de inicio del periodo de crisis")
    private LocalDateTime dateFrom;

    @NotNull(message = "La fecha de fin es obligatoria")
    @Schema(description = "Fecha de fin del periodo de crisis")
    private LocalDateTime dateTo;

    @NotEmpty(message = "El motivo es obligatorio")
    @Schema(description = "Motivo de la activación de la crisis")
    private String reason;
}
