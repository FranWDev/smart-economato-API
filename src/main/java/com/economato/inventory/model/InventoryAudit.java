package com.economato.inventory.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "inventory_audit",
    indexes = {
        @Index(name = "idx_inventory_audit_product", columnList = "product_id"),
        @Index(name = "idx_inventory_audit_user", columnList = "user_id"),
        @Index(name = "idx_inventory_audit_date", columnList = "movement_date"),
        @Index(name = "idx_inventory_audit_type", columnList = "movement_type")
    }
)
@EntityListeners(AuditingEntityListener.class)
public class InventoryAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Integer id;

    @NotNull(message = "El producto no puede ser nulo")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_inventory_audit_product"))
    private Product product;

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_inventory_audit_user"))
    private User users;

    @NotBlank(message = "El tipo de movimiento no puede estar vacío")
    @Pattern(regexp = "IN|OUT|ADJUSTMENT|RECEPTION|PRODUCTION", 
             message = "Tipo de movimiento inválido")
    @Column(name = "movement_type", nullable = false, length = 20)
    private String movementType;

    @NotNull(message = "La cantidad no puede ser nula")
    @Digits(integer = 10, fraction = 3)
    @Column(name = "quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
    @Column(name = "action_description", length = 500)
    private String actionDescription;

    @Column(name = "previous_state", columnDefinition = "TEXT")
    private String previousState;

    @Column(name = "new_state", columnDefinition = "TEXT")
    private String newState;

    @CreatedDate
    @Column(name = "movement_date", nullable = false, updatable = false)
    private LocalDateTime movementDate;
}
