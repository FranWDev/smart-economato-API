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

    @NotNull(message = "{orderdetail.notnull.el.pedido.no.puede.ser.nulo}")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("orderId")
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_detail_order"))
    private Order order;

    @NotNull(message = "{orderdetail.notnull.el.producto.no.puede.ser.nulo}")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("productId")
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_detail_product"))
    private Product product;

    @NotNull(message = "{orderdetail.notnull.la.cantidad.solicitada.no.pued}")
    @DecimalMin(value = "0.001", message = "{orderdetail.decimalmin.la.cantidad.debe.ser.mayor.a.0}")
    @Digits(integer = 10, fraction = 3)
    @Column(name = "requested_quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @DecimalMin(value = "0.0", inclusive = true, message = "{orderdetail.decimalmin.la.cantidad.recibida.no.puede.}")
    @Digits(integer = 10, fraction = 3)
    @Column(name = "received_quantity", precision = 10, scale = 3)
    private BigDecimal quantityReceived;
}
