package com.economato.inventory.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "order_header", indexes = {
        @Index(name = "idx_order_status", columnList = "status"),
        @Index(name = "idx_order_date", columnList = "order_date"),
        @Index(name = "idx_order_user", columnList = "user_id")
})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Integer id;

    @JsonIgnore
    @NotNull(message = "{order.notnull.el.usuario.no.puede.ser.nulo}")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_user"))
    private User users;

    @NotNull(message = "{order.notnull.la.fecha.del.pedido.no.puede.s}")
    @PastOrPresent(message = "{order.pastorpresent.la.fecha.del.pedido.no.puede.s}")
    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @NotBlank(message = "{order.notblank.el.estado.no.puede.estar.vac.o}")
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Version
    @Column(name = "version")
    private Long version;

    @JsonIgnore
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderDetail> details = new ArrayList<>();
}
