package com.economato.inventory.application.mapper.recipe;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.economato.inventory.application.dto.recipe.response.CookableRecipeResponseDTO;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.domain.model.recipe.RecipeComponent;

@Mapper(componentModel = "spring", uses = {
        AllergenMapper.class
}, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CookableRecipeMapper {

    @Mapping(target = "cookableQuantity", ignore = true)
    @Mapping(target = "cookable", ignore = true)
    @Mapping(source = "components", target = "components")
    @Mapping(source = "allergens", target = "allergens")
    CookableRecipeResponseDTO toResponseDTO(Recipe recipe);

    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    @Mapping(source = "product.unit", target = "unit")
    @Mapping(source = "quantity", target = "requiredQuantity")
    @Mapping(source = "product.currentStock", target = "availableStock")
    CookableRecipeResponseDTO.CookableRecipeComponentResponseDTO toComponentResponseDTO(RecipeComponent component);
}