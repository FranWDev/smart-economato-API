package com.economato.inventory.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "weekly_plan_slot",
    indexes = {
        @Index(name = "idx_slot_plan", columnList = "weekly_plan_id"),
        @Index(name = "idx_slot_day", columnList = "day_of_week"),
        @Index(name = "idx_slot_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeeklyPlanSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "weekly_plan_id", nullable = false, foreignKey = @ForeignKey(name = "fk_slot_weekly_plan"))
    private WeeklyPlan weeklyPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false, foreignKey = @ForeignKey(name = "fk_slot_recipe"))
    private Recipe recipe;

    @Column(name = "quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private WeeklyPlanSlotStatus status = WeeklyPlanSlotStatus.PENDING;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by_id", foreignKey = @ForeignKey(name = "fk_slot_confirmed_by"))
    private User confirmedBy;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @OneToMany(mappedBy = "slot", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WeeklyPlanSlotStudent> students = new ArrayList<>();
}
