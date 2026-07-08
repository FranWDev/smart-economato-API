package com.economato.inventory.domain.model.weeklyplan;
import com.economato.inventory.domain.model.user.User;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "weekly_plan_slot_student",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"slot_id", "student_id"})
    },
    indexes = {
        @Index(name = "idx_slot_student_slot", columnList = "slot_id"),
        @Index(name = "idx_slot_student_student", columnList = "student_id"),
        @Index(name = "idx_slot_student_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeeklyPlanSlotStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false, foreignKey = @ForeignKey(name = "fk_slot_student_slot"))
    private WeeklyPlanSlot slot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false, foreignKey = @ForeignKey(name = "fk_slot_student_user"))
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private StudentSlotStatus status = StudentSlotStatus.ASSIGNED;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_id", foreignKey = @ForeignKey(name = "fk_slot_student_cancelled_by"))
    private User cancelledBy;
}
