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

    @NotNull(message = "{validation.stockLedger.product.notNull}")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ledger_product"))
    private Product product;

    @NotNull(message = "{validation.stockLedger.quantityDelta.notNull}")
    @Digits(integer = 10, fraction = 3)
    @Column(name = "quantity_delta", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantityDelta;

    @NotNull(message = "{validation.stockLedger.resultingStock.notNull}")
    @DecimalMin(value = "0.0", inclusive = true)
    @Digits(integer = 10, fraction = 3)
    @Column(name = "resulting_stock", nullable = false, precision = 10, scale = 3)
    private BigDecimal resultingStock;

    @NotNull(message = "{validation.stockLedger.movementType.notNull}")
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 50)
    private MovementType movementType;

    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;

    @NotBlank(message = "{validation.stockLedger.previousHash.notBlank}")
    @Size(max = 64)
    @Column(name = "previous_hash", nullable = false, length = 64)
    private String previousHash;

    @NotBlank(message = "{validation.stockLedger.currentHash.notBlank}")
    @Size(max = 64)
    @Column(name = "current_hash", nullable = false, length = 64, unique = true)
    private String currentHash;

    @NotNull(message = "{validation.stockLedger.transactionTimestamp.notNull}")
    @Column(name = "transaction_timestamp", nullable = false)
    private LocalDateTime transactionTimestamp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_ledger_user"))
    private User user;

    @Column(name = "order_id")
    private Integer orderId;

    @NotNull(message = "{validation.stockLedger.sequenceNumber.notNull}")
    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    @Builder.Default
    @Column(name = "verified", nullable = false)
    private Boolean verified = false;
}
