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

    @NotBlank(message = "El nombre de la receta no puede estar vacío")
    @Size(min = 2, max = 150, message = "El nombre debe tener entre 2 y 150 caracteres")
    @Schema(description = "Nombre de la receta", example = "Paella Valenciana", minLength = 2, maxLength = 150)
    private String name;

    @NotBlank(message = "La elaboración no puede estar vacía")
    @Size(max = 2000, message = "La elaboración no puede exceder 2000 caracteres")
    @Schema(description = "Instrucciones de elaboración de la receta", example = "Cocer arroz, añadir ingredientes...")
    private String elaboration;

    @NotBlank(message = "La presentación no puede estar vacía")
    @Size(max = 1000, message = "La presentación no puede exceder 1000 caracteres")
    @Schema(description = "Descripción de la presentación del plato", example = "Servido en paellera tradicional")
    private String presentation;

    @NotEmpty(message = "Debe incluir al menos un componente en la receta")
    @Valid
    @Schema(description = "Lista de componentes de la receta")
    private List<RecipeComponentRequestDTO> components;

    @Schema(description = "IDs de alérgenos asociados a la receta", example = "[1, 3, 5]")
    private List<Integer> allergenIds;
}
