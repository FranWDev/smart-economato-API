package com.economato.inventory.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.economato.inventory.dto.projection.ProductProjection;
import com.economato.inventory.dto.request.ProductRequestDTO;
import com.economato.inventory.dto.response.ProductResponseDTO;
import com.economato.inventory.model.Product;
import com.economato.inventory.model.Supplier;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, uses = {
        SupplierMapper.class })
public interface ProductMapper {

    ProductResponseDTO toResponseDTO(Product product);

    @Mapping(source = "projection.supplier.id", target = "supplier.id")
    @Mapping(source = "projection.supplier.name", target = "supplier.name")
    @Mapping(source = "projection.isHidden", target = "hidden")
    ProductResponseDTO toResponseDTO(ProductProjection projection);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "orderDetails", ignore = true)
    @Mapping(target = "hidden", ignore = true)
    @Mapping(target = "supplier", source = "supplierId", qualifiedByName = "supplierIdToSupplier")
    @Mapping(target = "availabilityPercentage", source = "availabilityPercentage")
    @Mapping(target = "minimumStock", source = "minimumStock")
    Product toEntity(ProductRequestDTO requestDTO);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "orderDetails", ignore = true)
    @Mapping(target = "currentStock", ignore = true)
    @Mapping(target = "hidden", ignore = true)
    @Mapping(target = "supplier", source = "supplierId", qualifiedByName = "supplierIdToSupplier")
    @Mapping(target = "availabilityPercentage", source = "availabilityPercentage")
    @Mapping(target = "minimumStock", source = "minimumStock")
    void updateEntity(ProductRequestDTO requestDTO, @MappingTarget Product product);

    @Named("supplierIdToSupplier")
    default Supplier supplierIdToSupplier(Integer supplierId) {
        if (supplierId == null) {
            return null;
        }
        Supplier supplier = new Supplier();
        supplier.setId(supplierId);
        return supplier;
    }
}
