package com.economato.inventory.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "food_crisis")
public class FoodCrisis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "food_crisis_id")
    private Long id;

    @Column(name = "crisis_code", nullable = false, length = 50, unique = true)
    private String crisisCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "date_from", nullable = false)
    private LocalDateTime dateFrom;

    @Column(name = "date_to", nullable = false)
    private LocalDateTime dateTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CrisisStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activated_by_id")
    private User activatedBy;

    @Column(name = "activated_at", nullable = false)
    private LocalDateTime activatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lifted_by_id")
    private User liftedBy;

    @Column(name = "lifted_at")
    private LocalDateTime liftedAt;

    @OneToMany(mappedBy = "foodCrisis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CrisisAffectedProduct> affectedProducts;

    public enum CrisisStatus {
        ACTIVE, LIFTED
    }
}
