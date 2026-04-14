package com.economato.inventory.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para crear o actualizar un borrador de receta")
public class RecipeDraftRequestDTO {

    @NotBlank(message = "{validation.recipeDraftRequestDTO.name.notBlank}")
    @Size(min = 2, max = 150, message = "{validation.recipeDraftRequestDTO.name.size}")
    @Schema(description = "Nombre del borrador de receta", example = "Paella Valenciana")
    private String name;

    @NotBlank(message = "{validation.recipeDraftRequestDTO.elaboration.notBlank}")
    @Size(max = 2000, message = "{validation.recipeDraftRequestDTO.elaboration.size}")
    @Schema(description = "Instrucciones de elaboración del borrador", example = "Cocer arroz, añadir ingredientes...")
    private String elaboration;

    @NotBlank(message = "{validation.recipeDraftRequestDTO.presentation.notBlank}")
    @Size(max = 1000, message = "{validation.recipeDraftRequestDTO.presentation.size}")
    @Schema(description = "Descripción de la presentación del plato", example = "Servido en paellera tradicional")
    private String presentation;

    @NotNull(message = "{validation.recipeDraftRequestDTO.portions.notNull}")
    @DecimalMin(value = "0.01", message = "{validation.recipeDraftRequestDTO.portions.decimalMin}")
    @Schema(description = "Número de raciones que rinde el borrador", example = "10.0")
    private BigDecimal portions;

    @NotEmpty(message = "{validation.recipeDraftRequestDTO.components.notEmpty}")
    @Valid
    @Schema(description = "Lista de componentes del borrador")
    private List<RecipeComponentRequestDTO> components;

    @Schema(description = "Indica si el borrador está oculto", example = "false")
    private boolean isHidden;

    @Schema(description = "IDs de alérgenos asociados al borrador", example = "[1, 3, 5]")
    private List<Integer> allergenIds;
}