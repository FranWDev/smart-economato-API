package com.economato.inventory.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.economato.inventory.dto.response.OrderAuditResponseDTO;
import com.economato.inventory.model.OrderAudit;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface OrderAuditMapper {

    @Mapping(source = "order.id", target = "orderId")
    @Mapping(source = "users.id", target = "userId")
    @Mapping(source = "users.name", target = "userName")
    @Mapping(source = "previousState", target = "previousState")
    @Mapping(source = "newState", target = "newState")
    OrderAuditResponseDTO toResponseDTO(OrderAudit audit);
}
