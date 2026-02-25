package com.economato.inventory.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.economato.inventory.dto.projection.RecipeProjection;
import com.economato.inventory.dto.request.RecipeRequestDTO;
import com.economato.inventory.dto.response.RecipeResponseDTO;
import com.economato.inventory.model.Recipe;

import java.math.BigDecimal;

@Mapper(componentModel = "spring", uses = { RecipeComponentMapper.class,
        AllergenMapper.class }, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface RecipeMapper {

    @Mapping(source = "components", target = "components")
    @Mapping(source = "allergens", target = "allergens")
    @Mapping(source = ".", target = "totalCost", qualifiedByName = "calculateTotalCost")
    RecipeResponseDTO toResponseDTO(Recipe recipe);

    @Mapping(source = "projection.id", target = "id")
    @Mapping(source = "projection.name", target = "name")
    @Mapping(source = "projection.elaboration", target = "elaboration")
    @Mapping(source = "projection.presentation", target = "presentation")
    @Mapping(source = "projection.totalCost", target = "totalCost")
    @Mapping(source = "projection.components", target = "components")
    @Mapping(source = "projection.allergens", target = "allergens")
    RecipeResponseDTO toResponseDTO(RecipeProjection projection);

    @Named("calculateTotalCost")
    default BigDecimal calculateTotalCost(Recipe recipe) {
        if (recipe.getComponents() == null || recipe.getComponents().isEmpty()) {
            return BigDecimal.ZERO;
        }

        return recipe.getComponents().stream()
                .filter(component -> component.getQuantity() != null &&
                        component.getProduct() != null &&
                        component.getProduct().getUnitPrice() != null)
                .map(component -> component.getQuantity().multiply(component.getProduct().getUnitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Convierte RecipeRequestDTO a Recipe entidad (sin componentes ni alérgenos)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "totalCost", ignore = true)
    @Mapping(target = "components", ignore = true)
    @Mapping(target = "allergens", ignore = true)
    Recipe toEntity(RecipeRequestDTO requestDTO);

    /**
     * Actualiza una entidad Recipe existente con datos del DTO (sin componentes ni
     * alérgenos)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "totalCost", ignore = true)
    @Mapping(target = "components", ignore = true)
    @Mapping(target = "allergens", ignore = true)
    void updateEntity(RecipeRequestDTO requestDTO, @MappingTarget Recipe recipe);
}
