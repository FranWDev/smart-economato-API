package com.economato.inventory.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.economato.inventory.dto.response.OrderResponseDTO;
import com.economato.inventory.model.Order;

import java.math.BigDecimal;

@Mapper(componentModel = "spring", uses = {
        OrderDetailMapper.class }, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface OrderMapper {

    @Mapping(source = "users.id", target = "userId")
    @Mapping(source = "users.name", target = "userName")
    @Mapping(source = ".", target = "totalPrice", qualifiedByName = "calculateTotalPrice")
    OrderResponseDTO toResponseDTO(Order order);

    @Named("calculateTotalPrice")
    default BigDecimal calculateTotalPrice(Order order) {
        if (order.getDetails() == null || order.getDetails().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return order.getDetails().stream()
                .map(detail -> detail.getQuantity().multiply(detail.getProduct().getUnitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
