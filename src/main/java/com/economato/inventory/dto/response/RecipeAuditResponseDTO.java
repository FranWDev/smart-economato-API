package com.economato.inventory.dto.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO que representa una auditoría realizada sobre una receta")
public class RecipeAuditResponseDTO {

    @Schema(description = "ID de la receta asociada a la auditoría", example = "12")
    private Integer id_recipe;

    @Schema(description = "ID del usuario que realizó la acción", example = "3")
    private Integer id_user;

    @Schema(description = "Acción realizada sobre la receta", example = "ACTUALIZAR RECETA")
    private String action;

    @Schema(description = "Detalles de la acción realizada", example = "Se modificaron los componentes y el coste total")
    private String details;

    @Schema(description = "Fecha y hora en que se registró la auditoría", example = "2025-10-18T14:30:00")
    private LocalDateTime auditDate;
}
