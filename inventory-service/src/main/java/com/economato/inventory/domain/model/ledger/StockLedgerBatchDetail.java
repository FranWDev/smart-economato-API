package com.economato.inventory.domain.model.ledger;
import com.economato.inventory.domain.model.product.ProductBatch;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;

/**
 * Detalle de trazabilidad que vincula una transacción del ledger con el lote específico afectado.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "stock_ledger_batch_detail", indexes = {
        @Index(name = "idx_ledger_batch_ledger", columnList = "ledger_transaction_id"),
        @Index(name = "idx_ledger_batch_batch", columnList = "batch_id")
})
public class StockLedgerBatchDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "detail_id")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ledger_transaction_id", nullable = false, foreignKey = @ForeignKey(name = "fk_detail_ledger"))
    private StockLedger ledgerTransaction;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false, foreignKey = @ForeignKey(name = "fk_detail_batch"))
    private ProductBatch batch;

    @NotNull
    @Column(name = "quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;
}
