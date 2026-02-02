package com.economatom.inventory.mapper;

import com.economatom.inventory.dto.request.SupplierRequestDTO;
import com.economatom.inventory.dto.response.SupplierResponseDTO;
import com.economatom.inventory.model.Supplier;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface SupplierMapper {

    SupplierResponseDTO toResponseDTO(Supplier supplier);

    Supplier toEntity(SupplierRequestDTO requestDTO);

    void updateEntity(SupplierRequestDTO requestDTO, @MappingTarget Supplier supplier);
}
