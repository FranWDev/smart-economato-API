package com.economato.inventory.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.economato.inventory.dto.request.SupplierRequestDTO;
import com.economato.inventory.dto.response.SupplierResponseDTO;
import com.economato.inventory.model.Supplier;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface SupplierMapper {

    SupplierResponseDTO toResponseDTO(Supplier supplier);

    Supplier toEntity(SupplierRequestDTO requestDTO);

    void updateEntity(SupplierRequestDTO requestDTO, @MappingTarget Supplier supplier);
}
