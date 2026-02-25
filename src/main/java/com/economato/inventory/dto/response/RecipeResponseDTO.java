package com.economato.inventory.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO de respuesta con los datos de una receta")
public class RecipeResponseDTO {

    @Schema(description = "Identificador único de la receta", example = "1")
    private Integer id;

    @Schema(description = "Nombre de la receta", example = "Paella Valenciana")
    private String name;

    @Schema(description = "Instrucciones de elaboración de la receta", example = "Cocer arroz, añadir ingredientes...")
    private String elaboration;

    @Schema(description = "Descripción de la presentación del plato", example = "Servido en paellera tradicional")
    private String presentation;

    @Schema(description = "Costo total de la receta", example = "12.50")
    private BigDecimal totalCost;

    @Schema(description = "Indica si la receta está oculta", example = "false")
    private boolean isHidden;

    @Schema(description = "Lista de componentes de la receta")
    private List<RecipeComponentResponseDTO> components;

    @Schema(description = "Lista de alérgenos asociados a la receta")
    private List<AllergenResponseDTO> allergens;
}
