package com.economato.inventory.domain.model.stock;
import com.economato.inventory.domain.model.product.Product;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "stock_weekly_consumption_history")
public class StockWeeklyConsumptionHistory {

    @Id
    @Column(name = "product_id")
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "product_id")
    private Product product;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "stock_weekly_consumption_value", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "week_index")
    @Column(name = "consumption_value", precision = 19, scale = 3, nullable = false)
    @Builder.Default
    private List<BigDecimal> weeklyConsumption = new ArrayList<>();

    @Column(name = "weeks_of_history", nullable = false)
    private int weeksOfHistory;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
