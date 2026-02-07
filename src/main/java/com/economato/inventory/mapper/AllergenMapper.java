package com.economato.inventory.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.economato.inventory.dto.request.AllergenRequestDTO;
import com.economato.inventory.dto.response.AllergenResponseDTO;
import com.economato.inventory.model.Allergen;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface AllergenMapper {

    AllergenResponseDTO toResponseDTO(Allergen allergen);

    Allergen toEntity(AllergenRequestDTO requestDTO);

    void updateEntity(AllergenRequestDTO requestDTO, @MappingTarget Allergen allergen);
}
