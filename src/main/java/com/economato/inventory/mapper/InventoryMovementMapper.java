package com.economato.inventory.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.economato.inventory.dto.response.InventoryMovementResponseDTO;
import com.economato.inventory.model.InventoryAudit;

import java.math.BigDecimal;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface InventoryMovementMapper {

    @Mapping(source = "audit.id", target = "id")
    @Mapping(source = "audit.product.id", target = "productId")
    @Mapping(source = "audit.product.name", target = "productName")
    @Mapping(source = "audit.users.id", target = "userId")
    @Mapping(source = "audit.users.name", target = "userName")
    @Mapping(source = "audit.quantity", target = "quantity")
    @Mapping(source = "audit.movementType", target = "movementType")
    @Mapping(source = "audit.movementDate", target = "movementDate")
    @Mapping(source = "previousStock", target = "previousStock")
    @Mapping(source = "audit.product.currentStock", target = "currentStock")
    InventoryMovementResponseDTO toResponseDTO(InventoryAudit audit, BigDecimal previousStock);

    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    @Mapping(source = "users.id", target = "userId")
    @Mapping(source = "users.name", target = "userName")
    @Mapping(source = "product.currentStock", target = "previousStock")
    @Mapping(source = "product.currentStock", target = "currentStock")
    InventoryMovementResponseDTO toResponseDTO(InventoryAudit audit);
}
