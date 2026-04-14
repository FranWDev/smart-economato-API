package com.economato.inventory.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para rechazar un borrador de receta")
public class RecipeDraftRejectRequestDTO {

    @NotBlank(message = "{validation.recipeDraftRejectRequestDTO.reason.notBlank}")
    @Schema(description = "Motivo del rechazo", example = "Falta detallar los alérgenos")
    private String reason;
}