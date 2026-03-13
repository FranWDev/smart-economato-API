package com.economato.inventory.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "crisis_affected_product")
public class CrisisAffectedProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_crisis_id", nullable = false)
    private FoodCrisis foodCrisis;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "original_availability_percentage", precision = 5, scale = 2, nullable = false)
    private BigDecimal originalAvailabilityPercentage;
}
