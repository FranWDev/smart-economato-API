package com.economatom.inventory.model;

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

    @NotNull(message = "El stock actual no puede ser nulo")
    @DecimalMin(value = "0.0", inclusive = true)
    @Digits(integer = 10, fraction = 3)
    @Column(name = "current_stock", nullable = false, precision = 10, scale = 3)
    private BigDecimal currentStock;

    @NotBlank(message = "El último hash no puede estar vacío")
    @Size(max = 64)
    @Column(name = "last_transaction_hash", nullable = false, length = 64)
    private String lastTransactionHash;

    @NotNull(message = "El número de secuencia no puede ser nulo")
    @Column(name = "last_sequence_number", nullable = false)
    private Long lastSequenceNumber;

    @NotNull(message = "La fecha de actualización no puede ser nula")
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Column(name = "last_verified")
    private LocalDateTime lastVerified;

    @Builder.Default
    @NotBlank(message = "El estado de integridad no puede estar vacío")
    @Size(max = 20)
    @Column(name = "integrity_status", nullable = false, length = 20)
    private String integrityStatus = "UNVERIFIED";

    @Version
    @Column(name = "version")
    private Long version;
}
