package com.economato.inventory.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "order_audit",
    indexes = {
        @Index(name = "idx_order_audit_order", columnList = "order_id"),
        @Index(name = "idx_order_audit_user", columnList = "user_id"),
        @Index(name = "idx_order_audit_date", columnList = "audit_date"),
        @Index(name = "idx_order_audit_action", columnList = "action")
    }
)
@EntityListeners(AuditingEntityListener.class)
public class OrderAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", foreignKey = @ForeignKey(name = "fk_order_audit_order", foreignKeyDefinition = "FOREIGN KEY (order_id) REFERENCES order_header(order_id) ON DELETE SET NULL"))
    private Order order;

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_order_audit_user"))
    private User users;

    @NotBlank(message = "{validation.orderAudit.action.notBlank}")
    @Size(max = 100)
    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Size(max = 1000, message = "{validation.orderAudit.details.size}")
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "previous_state", columnDefinition = "TEXT")
    private String previousState;

    @Column(name = "new_state", columnDefinition = "TEXT")
    private String newState;

    @CreatedDate
    @Column(name = "audit_date", nullable = false, updatable = false)
    private LocalDateTime auditDate;
}
