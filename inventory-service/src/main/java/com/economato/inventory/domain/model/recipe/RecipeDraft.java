package com.economato.inventory.domain.model.recipe;
import com.economato.inventory.domain.model.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "recipe_draft", indexes = {
        @Index(name = "idx_recipe_draft_status", columnList = "status"),
        @Index(name = "idx_recipe_draft_created_by", columnList = "created_by")
})
@EntityListeners(AuditingEntityListener.class)
public class RecipeDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recipe_draft_id")
    private Integer id;

    @Column(name = "recipe_name", nullable = false, length = 150)
    @NotBlank(message = "{validation.recipe.name.notBlank}")
    @Size(min = 2, max = 150, message = "{validation.recipe.name.size}")
    private String name;

    @Column(name = "elaboration", columnDefinition = "TEXT")
    @Size(max = 2000, message = "{validation.recipe.elaboration.size}")
    private String elaboration;

    @Column(name = "presentation", columnDefinition = "TEXT")
    @Size(max = 1000, message = "{validation.recipe.presentation.size}")
    private String presentation;

    @Column(name = "portions", precision = 10, scale = 2)
    @Builder.Default
    @DecimalMin(value = "0.01", message = "{validation.recipe.portions.decimalMin}")
    private BigDecimal portions = BigDecimal.ONE;

    @Column(name = "components_json", columnDefinition = "TEXT", nullable = false)
    private String componentsJson;

    @Column(name = "allergen_ids_json", columnDefinition = "TEXT")
    private String allergenIdsJson;

    @Column(name = "is_hidden", nullable = false)
    @Builder.Default
    private boolean isHidden = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RecipeDraftStatus status = RecipeDraftStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "approved_recipe_id")
    private Integer approvedRecipeId;

    @Version
    @Column(name = "version")
    private Long version;

    public boolean isEditable() {
        return status == RecipeDraftStatus.PENDING || status == RecipeDraftStatus.REJECTED;
    }
}