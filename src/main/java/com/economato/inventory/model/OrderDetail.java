package com.economato.inventory.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "order_detail",
    indexes = {
        @Index(name = "idx_order_detail_order", columnList = "order_id"),
        @Index(name = "idx_order_detail_product", columnList = "product_id")
    }
)
public class OrderDetail {

    @EmbeddedId
    private OrderDetailId id = new OrderDetailId();

    @NotNull(message = "{validation.orderDetail.order.notNull}")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("orderId")
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_detail_order"))
    private Order order;

    @NotNull(message = "{validation.orderDetail.product.notNull}")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("productId")
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_detail_product"))
    private Product product;

    @NotNull(message = "{validation.orderDetail.quantity.notNull}")
    @DecimalMin(value = "0.001", message = "{validation.orderDetail.quantity.decimalMin}")
    @Digits(integer = 10, fraction = 3)
    @Column(name = "requested_quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @DecimalMin(value = "0.0", inclusive = true, message = "{validation.orderDetail.quantityReceived.decimalMin}")
    @Digits(integer = 10, fraction = 3)
    @Column(name = "received_quantity", precision = 10, scale = 3)
    private BigDecimal quantityReceived;
}
