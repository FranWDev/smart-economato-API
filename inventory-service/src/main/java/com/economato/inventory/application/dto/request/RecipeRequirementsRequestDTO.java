package com.economato.inventory.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeRequirementsRequestDTO {

    @NotEmpty(message = "{error.recipe.requirements.empty}")
    @Valid
    private List<RecipeQuantityRequestDTO> recipes;
}
