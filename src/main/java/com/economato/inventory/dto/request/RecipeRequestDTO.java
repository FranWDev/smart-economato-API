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

    @NotBlank(message = "{reciperequestdto.notblank.el.nombre.de.la.receta.no.pued}")
    @Size(min = 2, max = 150, message = "{reciperequestdto.size.el.nombre.debe.tener.entre.2.y}")
    @Schema(description = "Nombre de la receta", example = "Paella Valenciana", minLength = 2, maxLength = 150)
    private String name;

    @NotBlank(message = "{reciperequestdto.notblank.la.elaboraci.n.no.puede.estar.}")
    @Size(max = 2000, message = "{reciperequestdto.size.la.elaboraci.n.no.puede.excede}")
    @Schema(description = "Instrucciones de elaboración de la receta", example = "Cocer arroz, añadir ingredientes...")
    private String elaboration;

    @NotBlank(message = "{reciperequestdto.notblank.la.presentaci.n.no.puede.estar}")
    @Size(max = 1000, message = "{reciperequestdto.size.la.presentaci.n.no.puede.exced}")
    @Schema(description = "Descripción de la presentación del plato", example = "Servido en paellera tradicional")
    private String presentation;

    @NotEmpty(message = "{reciperequestdto.notempty.debe.incluir.al.menos.un.compo}")
    @Valid
    @Schema(description = "Lista de componentes de la receta")
    private List<RecipeComponentRequestDTO> components;

    @Schema(description = "IDs de alérgenos asociados a la receta", example = "[1, 3, 5]")
    private List<Integer> allergenIds;
}
