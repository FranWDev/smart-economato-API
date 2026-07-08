package com.economato.inventory.application.mapper.recipe;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.economato.inventory.application.dto.recipe.projection.AllergenProjection;
import com.economato.inventory.application.dto.recipe.request.AllergenRequestDTO;
import com.economato.inventory.application.dto.recipe.response.AllergenResponseDTO;
import com.economato.inventory.domain.model.recipe.Allergen;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface AllergenMapper {

    AllergenResponseDTO toResponseDTO(Allergen allergen);

    Allergen toEntity(AllergenRequestDTO requestDTO);

    void updateEntity(AllergenRequestDTO requestDTO, @MappingTarget Allergen allergen);

    AllergenResponseDTO toResponseDTO(AllergenProjection projection);
}
