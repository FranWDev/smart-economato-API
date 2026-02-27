package com.economato.inventory.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para crear o actualizar una receta")
public class RecipeRequestDTO {

    @NotBlank(message = "{validation.recipeRequestDTO.name.notBlank}")
    @Size(min = 2, max = 150, message = "{validation.recipeRequestDTO.name.size}")
    @Schema(description = "Nombre de la receta", example = "Paella Valenciana", minLength = 2, maxLength = 150)
    private String name;

    @NotBlank(message = "{validation.recipeRequestDTO.elaboration.notBlank}")
    @Size(max = 2000, message = "{validation.recipeRequestDTO.elaboration.size}")
    @Schema(description = "Instrucciones de elaboración de la receta", example = "Cocer arroz, añadir ingredientes...")
    private String elaboration;

    @NotBlank(message = "{validation.recipeRequestDTO.presentation.notBlank}")
    @Size(max = 1000, message = "{validation.recipeRequestDTO.presentation.size}")
    @Schema(description = "Descripción de la presentación del plato", example = "Servido en paellera tradicional")
    private String presentation;

    @NotEmpty(message = "{validation.recipeRequestDTO.components.notEmpty}")
    @Valid
    @Schema(description = "Lista de componentes de la receta")
    private List<RecipeComponentRequestDTO> components;

    @Schema(description = "IDs de alérgenos asociados a la receta", example = "[1, 3, 5]")
    private List<Integer> allergenIds;
}
