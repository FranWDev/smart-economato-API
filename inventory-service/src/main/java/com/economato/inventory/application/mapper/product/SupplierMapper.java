package com.economato.inventory.application.mapper.product;

import com.economato.inventory.application.dto.product.projection.SupplierProjection;
import com.economato.inventory.application.dto.product.request.SupplierRequestDTO;
import com.economato.inventory.application.dto.product.response.SupplierResponseDTO;
import com.economato.inventory.domain.model.product.Supplier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface SupplierMapper {

    SupplierResponseDTO toResponseDTO(Supplier supplier);

    SupplierResponseDTO toResponseDTO(SupplierProjection projection);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "products", ignore = true)
    Supplier toEntity(SupplierRequestDTO requestDTO);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "products", ignore = true)
    void updateEntity(SupplierRequestDTO requestDTO, @MappingTarget Supplier supplier);
}
