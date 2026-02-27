package com.economato.inventory.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para crear o actualizar un alérgeno.")
public class AllergenRequestDTO {

    @JsonProperty("name")
    @NotBlank(message = "{validation.allergenRequestDTO.name.notBlank}")
    @Size(min = 2, max = 50, message = "{validation.allergenRequestDTO.name.size}")
    @Schema(description = "Nombre del alérgeno", example = "Gluten")
    private String name;
}
