package com.economato.inventory.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Auditoría específica para el cocinado de recetas.
 * Registra quién cocina qué receta y en qué cantidad.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "recipe_cooking_audit", indexes = {
        @Index(name = "idx_cooking_audit_recipe", columnList = "recipe_id"),
        @Index(name = "idx_cooking_audit_user", columnList = "user_id"),
        @Index(name = "idx_cooking_audit_date", columnList = "cooking_date")
})
@EntityListeners(AuditingEntityListener.class)
public class RecipeCookingAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cooking_audit_id")
    private Long id;

    @NotNull(message = "{validation.recipeCookingAudit.recipe.notNull}")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cooking_audit_recipe"))
    private Recipe recipe;

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_cooking_audit_user"))
    private User user;

    @NotNull(message = "{validation.recipeCookingAudit.quantityCooked.notNull}")
    @DecimalMin(value = "0.001", message = "{validation.recipeCookingAudit.quantityCooked.decimalMin}")
    @Digits(integer = 10, fraction = 3)
    @Column(name = "quantity_cooked", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantityCooked;

    @Size(max = 1000, message = "{validation.recipeCookingAudit.details.size}")
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "components_state", columnDefinition = "TEXT")
    private String componentsState;

    @CreatedDate
    @Column(name = "cooking_date", nullable = false, updatable = false)
    private LocalDateTime cookingDate;
}
