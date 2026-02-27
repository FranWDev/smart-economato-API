package com.economato.inventory.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.economato.inventory.dto.response.OrderAuditResponseDTO;
import com.economato.inventory.model.OrderAudit;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface OrderAuditMapper {

    @Mapping(source = "order.id", target = "orderId")
    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.name", target = "userName")
    OrderAuditResponseDTO toResponseDTO(OrderAudit audit);

    @Mapping(source = "order.id", target = "orderId")
    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.name", target = "userName")
    OrderAuditResponseDTO toResponseDTO(com.economato.inventory.dto.projection.OrderAuditProjection projection);
}
