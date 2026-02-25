package com.economato.inventory.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.economato.inventory.dto.request.OrderDetailRequestDTO;
import com.economato.inventory.dto.projection.OrderProjection;
import com.economato.inventory.dto.projection.OrderDetailProjection;
import com.economato.inventory.dto.response.OrderDetailResponseDTO;
import com.economato.inventory.model.OrderDetail;

import java.math.BigDecimal;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface OrderDetailMapper {

    @Mapping(source = "order.id", target = "orderId")
    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    @Mapping(source = "product.unitPrice", target = "unitPrice")
    @Mapping(source = ".", target = "subtotal", qualifiedByName = "calculateSubtotal")
    OrderDetailResponseDTO toResponseDTO(OrderDetail orderDetail);

    @Mapping(source = "projection.order.id", target = "orderId")
    @Mapping(source = "projection.product.id", target = "productId")
    @Mapping(source = "projection.product.name", target = "productName")
    @Mapping(source = "projection.product.unitPrice", target = "unitPrice")
    @Mapping(source = "projection.quantity", target = "quantity")
    @Mapping(source = "projection.quantityReceived", target = "quantityReceived")
    @Mapping(source = "projection", target = "subtotal", qualifiedByName = "calculateSubtotalFromProjection")
    OrderDetailResponseDTO toResponseDTO(OrderDetailProjection projection);

    @Mapping(target = "orderId", ignore = true)
    @Mapping(source = "summary.product.id", target = "productId")
    @Mapping(source = "summary.product.name", target = "productName")
    @Mapping(source = "summary.product.unitPrice", target = "unitPrice")
    @Mapping(source = "summary.quantity", target = "quantity")
    @Mapping(source = "summary.quantityReceived", target = "quantityReceived")
    @Mapping(source = "summary", target = "subtotal", qualifiedByName = "calculateSubtotalFromSummary")
    OrderDetailResponseDTO toResponseDTO(OrderProjection.OrderDetailSummary summary);

    @Named("calculateSubtotalFromSummary")
    default BigDecimal calculateSubtotalFromSummary(OrderProjection.OrderDetailSummary summary) {
        if (summary.getQuantity() == null || summary.getProduct() == null ||
                summary.getProduct().getUnitPrice() == null) {
            return BigDecimal.ZERO;
        }
        return summary.getQuantity().multiply(summary.getProduct().getUnitPrice());
    }

    @Named("calculateSubtotalFromProjection")
    default BigDecimal calculateSubtotalFromProjection(OrderDetailProjection projection) {
        // The provided code edit contained controller-related annotations and method
        // signature
        // within this mapper's default method, which is syntactically incorrect.
        // I am applying the rest of the original method's body as it was before the
        // edit.
        if (projection.getQuantity() == null || projection.getProduct() == null ||
                projection.getProduct().getUnitPrice() == null) {
            return BigDecimal.ZERO;
        }
        return projection.getQuantity().multiply(projection.getProduct().getUnitPrice());
    }

    @Named("calculateSubtotal")
    default BigDecimal calculateSubtotal(OrderDetail orderDetail) {
        if (orderDetail.getQuantity() == null || orderDetail.getProduct() == null ||
                orderDetail.getProduct().getUnitPrice() == null) {
            return BigDecimal.ZERO;
        }
        return orderDetail.getQuantity().multiply(orderDetail.getProduct().getUnitPrice());
    }

    @Mapping(target = "order", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "quantityReceived", ignore = true)
    OrderDetail toEntity(OrderDetailRequestDTO dto);

    @Mapping(target = "order", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "quantityReceived", ignore = true)
    void updateEntityFromDto(OrderDetailRequestDTO dto, @org.mapstruct.MappingTarget OrderDetail entity);
}
