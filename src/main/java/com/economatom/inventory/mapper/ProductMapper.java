package com.economatom.inventory.mapper;

import com.economatom.inventory.dto.request.ProductRequestDTO;
import com.economatom.inventory.dto.response.ProductResponseDTO;
import com.economatom.inventory.model.Product;
import com.economatom.inventory.model.Supplier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring", 
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    uses = {SupplierMapper.class}
)
public interface ProductMapper {

    ProductResponseDTO toResponseDTO(Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "orderDetails", ignore = true)
    @Mapping(target = "supplier", source = "supplierId", qualifiedByName = "supplierIdToSupplier")
    Product toEntity(ProductRequestDTO requestDTO);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "orderDetails", ignore = true)
    @Mapping(target = "supplier", source = "supplierId", qualifiedByName = "supplierIdToSupplier")
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

