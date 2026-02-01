package com.economatom.inventory.mapper;

import com.economatom.inventory.dto.response.OrderAuditResponseDTO;
import com.economatom.inventory.model.OrderAudit;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface OrderAuditMapper {

    @Mapping(source = "order.id", target = "orderId")
    @Mapping(source = "users.id", target = "userId")
    @Mapping(source = "users.name", target = "userName")
    OrderAuditResponseDTO toResponseDTO(OrderAudit audit);
}
