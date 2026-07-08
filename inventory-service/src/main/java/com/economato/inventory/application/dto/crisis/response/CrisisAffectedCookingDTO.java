package com.economato.inventory.application.dto.crisis.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resumen de cocinado afectado durante una crisis")
public class CrisisAffectedCookingDTO {

    @Schema(description = "ID de la auditoría de cocina", example = "15")
    private Long cookingAuditId;

    @Schema(description = "Nombre de la receta cocinada", example = "Escalope Milanesa")
    private String recipeName;

    @Schema(description = "Nombre del usuario que realizó el cocinado", example = "María López")
    private String userName;

    @Schema(description = "Fecha y hora del cocinado")
    private LocalDateTime cookingDate;

    @Schema(description = "Cantidad de raciones cocinadas", example = "12.0")
    private Double quantityCooked;
}
