package com.economato.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO de respuesta que representa un alérgeno en el sistema.")
public class AllergenResponseDTO {

    @Schema(description = "ID único del alérgeno", example = "1")
    private Integer id;

    @Schema(description = "Nombre del alérgeno", example = "Gluten")
    private String name;
}
