package com.economato.inventory.domain.model.product;
import com.economato.inventory.domain.model.ledger.StockLedger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "product_batch", indexes = {
        @Index(name = "idx_batch_product", columnList = "product_id"),
        @Index(name = "idx_batch_expiration", columnList = "expiration_date"),
        @Index(name = "idx_batch_remaining", columnList = "remaining_quantity")
})
public class ProductBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "batch_id")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_batch_product"))
    private Product product;

    @NotNull(message = "La fecha de caducidad es obligatoria")
    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    @Size(max = 100)
    @Column(name = "batch_code", length = 100)
    private String batchCode;

    @NotNull
    @Digits(integer = 10, fraction = 3)
    @Column(name = "initial_quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal initialQuantity;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    @Digits(integer = 10, fraction = 3)
    @Column(name = "remaining_quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal remainingQuantity;

    @NotNull
    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ledger_transaction_id", foreignKey = @ForeignKey(name = "fk_batch_ledger"))
    private StockLedger ledgerTransaction;

    @Column(name = "is_depleted", nullable = false)
    private boolean depleted;

    @Version
    @Column(name = "version")
    private Long version;
}
