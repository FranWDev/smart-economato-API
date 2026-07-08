package com.economato.inventory.domain.model.incident;
import com.economato.inventory.domain.model.recipe.RecipeCookingAudit;
import com.economato.inventory.domain.model.user.User;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "incident_audit_attachment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_incident_audit_attachment", columnNames = {"incident_id", "cooking_audit_id"})
})
public class IncidentAuditAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "incident_audit_attachment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, foreignKey = @ForeignKey(name = "fk_incident_attachment_incident"))
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cooking_audit_id", nullable = false, foreignKey = @ForeignKey(name = "fk_incident_attachment_cooking_audit"))
    private RecipeCookingAudit cookingAudit;

    @Column(name = "attached_at", nullable = false)
    private LocalDateTime attachedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attached_by", nullable = false, foreignKey = @ForeignKey(name = "fk_incident_attachment_attached_by"))
    private User attachedBy;

    @Column(name = "reverted", nullable = false)
    @Builder.Default
    private boolean reverted = false;

    @Column(name = "reverted_at")
    private LocalDateTime revertedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reverted_by", foreignKey = @ForeignKey(name = "fk_incident_attachment_reverted_by"))
    private User revertedBy;
}
