package com.economato.inventory.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "stock_ledger", indexes = {
        @Index(name = "idx_ledger_product", columnList = "product_id"),
        @Index(name = "idx_ledger_timestamp", columnList = "transaction_timestamp"),
        @Index(name = "idx_ledger_type", columnList = "movement_type"),
        @Index(name = "idx_ledger_prev_hash", columnList = "previous_hash")
})
public class StockLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long id;

    @NotNull(message = "{stockledger.notnull.el.producto.no.puede.ser.nulo}")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ledger_product"))
    private Product product;

    @NotNull(message = "{stockledger.notnull.la.cantidad.no.puede.ser.nula}")
    @Digits(integer = 10, fraction = 3)
    @Column(name = "quantity_delta", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantityDelta;

    @NotNull(message = "{stockledger.notnull.el.stock.resultante.no.puede.s}")
    @DecimalMin(value = "0.0", inclusive = true)
    @Digits(integer = 10, fraction = 3)
    @Column(name = "resulting_stock", nullable = false, precision = 10, scale = 3)
    private BigDecimal resultingStock;

    @NotNull(message = "{stockledger.notnull.el.tipo.de.movimiento.no.puede}")
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 50)
    private MovementType movementType;

    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;

    @NotBlank(message = "{stockledger.notblank.el.hash.previo.no.puede.estar.}")
    @Size(max = 64)
    @Column(name = "previous_hash", nullable = false, length = 64)
    private String previousHash;

    @NotBlank(message = "{stockledger.notblank.el.hash.actual.no.puede.estar.}")
    @Size(max = 64)
    @Column(name = "current_hash", nullable = false, length = 64, unique = true)
    private String currentHash;

    @NotNull(message = "{stockledger.notnull.el.timestamp.no.puede.ser.nulo}")
    @Column(name = "transaction_timestamp", nullable = false)
    private LocalDateTime transactionTimestamp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_ledger_user"))
    private User user;

    @Column(name = "order_id")
    private Integer orderId;

    @NotNull(message = "{stockledger.notnull.el.n.mero.de.secuencia.no.pued}")
    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    @Builder.Default
    @Column(name = "verified", nullable = false)
    private Boolean verified = false;
}
