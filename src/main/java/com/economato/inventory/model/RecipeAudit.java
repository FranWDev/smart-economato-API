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
@Table(name = "recipe_audit", indexes = {
        @Index(name = "idx_recipe_audit_recipe", columnList = "recipe_id"),
        @Index(name = "idx_recipe_audit_user", columnList = "user_id"),
        @Index(name = "idx_recipe_audit_date", columnList = "audit_date"),
        @Index(name = "idx_recipe_audit_action", columnList = "action")
})
@EntityListeners(AuditingEntityListener.class)
public class RecipeAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", foreignKey = @ForeignKey(name = "fk_recipe_audit_recipe", foreignKeyDefinition = "FOREIGN KEY (recipe_id) REFERENCES recipe(recipe_id) ON DELETE SET NULL"))
    private Recipe recipe;

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_recipe_audit_user"))
    private User user;

    @NotBlank(message = "{validation.recipeAudit.action.notBlank}")
    @Size(max = 100)
    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Size(max = 1000, message = "{validation.recipeAudit.details.size}")
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
