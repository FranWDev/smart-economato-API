package com.economato.inventory.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.economato.inventory.dto.projection.SupplierProjection;
import com.economato.inventory.dto.request.SupplierRequestDTO;
import com.economato.inventory.dto.response.SupplierResponseDTO;
import com.economato.inventory.model.Supplier;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface SupplierMapper {

    SupplierResponseDTO toResponseDTO(Supplier supplier);

    SupplierResponseDTO toResponseDTO(SupplierProjection projection);

    @org.mapstruct.Mapping(target = "id", ignore = true)
    @org.mapstruct.Mapping(target = "products", ignore = true)
    Supplier toEntity(SupplierRequestDTO requestDTO);

    @org.mapstruct.Mapping(target = "id", ignore = true)
    @org.mapstruct.Mapping(target = "products", ignore = true)
    void updateEntity(SupplierRequestDTO requestDTO, @MappingTarget Supplier supplier);
}
