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
@Table(name = "stock_snapshot", indexes = {
        @Index(name = "idx_snapshot_updated", columnList = "last_updated")
})
public class StockSnapshot {

    @Id
    @Column(name = "product_id")
    private Integer productId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "product_id", foreignKey = @ForeignKey(name = "fk_snapshot_product"))
    private Product product;

    @NotNull(message = "{stocksnapshot.notnull.el.stock.actual.no.puede.ser.n}")
    @DecimalMin(value = "0.0", inclusive = true)
    @Digits(integer = 10, fraction = 3)
    @Column(name = "current_stock", nullable = false, precision = 10, scale = 3)
    private BigDecimal currentStock;

    @NotBlank(message = "{stocksnapshot.notblank.el.ltimo.hash.no.puede.estar.v}")
    @Size(max = 64)
    @Column(name = "last_transaction_hash", nullable = false, length = 64)
    private String lastTransactionHash;

    @NotNull(message = "{stocksnapshot.notnull.el.n.mero.de.secuencia.no.pued}")
    @Column(name = "last_sequence_number", nullable = false)
    private Long lastSequenceNumber;

    @NotNull(message = "{stocksnapshot.notnull.la.fecha.de.actualizaci.n.no.p}")
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Column(name = "last_verified")
    private LocalDateTime lastVerified;

    @Builder.Default
    @NotBlank(message = "{stocksnapshot.notblank.el.estado.de.integridad.no.pue}")
    @Size(max = 20)
    @Column(name = "integrity_status", nullable = false, length = 20)
    private String integrityStatus = "UNVERIFIED";

    @Version
    @Column(name = "version")
    private Long version;
}
