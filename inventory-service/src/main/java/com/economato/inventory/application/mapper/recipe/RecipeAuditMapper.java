package com.economato.inventory.application.mapper.recipe;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.economato.inventory.application.dto.recipe.projection.RecipeAuditProjection;
import com.economato.inventory.application.dto.recipe.response.RecipeAuditResponseDTO;
import com.economato.inventory.domain.model.recipe.RecipeAudit;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface RecipeAuditMapper {

    @Mapping(source = "recipe.id", target = "id_recipe")
    @Mapping(source = "user.id", target = "id_user")
    @Mapping(source = "previousState", target = "previousState")
    @Mapping(source = "newState", target = "newState")
    RecipeAuditResponseDTO toResponseDTO(RecipeAudit audit);

    @Mapping(source = "recipe.id", target = "id_recipe")
    @Mapping(source = "user.id", target = "id_user")
    RecipeAuditResponseDTO toResponseDTO(RecipeAuditProjection projection);
}
