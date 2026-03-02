package com.economato.inventory.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Almacena el resultado persistente de la predicción de consumo para un
 * producto.
 * Se actualiza de forma asíncrona tras eventos de cocina (Kafka).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "stock_prediction")
public class StockPrediction {

    @Id
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "product_id")
    private Product product;

    /** Consumo proyectado para los próximos 14 días. */
    @Column(name = "projected_consumption", precision = 19, scale = 4)
    private BigDecimal projectedConsumption;

    /** Fecha de la última actualización del cálculo. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
