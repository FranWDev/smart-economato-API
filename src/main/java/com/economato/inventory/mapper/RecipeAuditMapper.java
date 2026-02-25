package com.economato.inventory.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.economato.inventory.dto.response.RecipeAuditResponseDTO;
import com.economato.inventory.model.RecipeAudit;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface RecipeAuditMapper {

    @Mapping(source = "recipe.id", target = "id_recipe")
    @Mapping(source = "users.id", target = "id_user")
    @Mapping(source = "previousState", target = "previousState")
    @Mapping(source = "newState", target = "newState")
    RecipeAuditResponseDTO toResponseDTO(RecipeAudit audit);

    @Mapping(source = "recipe.id", target = "id_recipe")
    @Mapping(source = "users.id", target = "id_user")
    RecipeAuditResponseDTO toResponseDTO(com.economato.inventory.dto.projection.RecipeAuditProjection projection);
}
