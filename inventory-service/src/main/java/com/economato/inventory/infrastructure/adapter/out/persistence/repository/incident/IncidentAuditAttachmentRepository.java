package com.economato.inventory.infrastructure.adapter.out.persistence.repository.incident;

import com.economato.inventory.domain.model.incident.IncidentAuditAttachment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentAuditAttachmentRepository extends JpaRepository<IncidentAuditAttachment, Long> {

    @EntityGraph(attributePaths = {"cookingAudit", "cookingAudit.recipe", "cookingAudit.user", "incident"})
    @Query("SELECT a FROM IncidentAuditAttachment a WHERE a.id = :id")
    Optional<IncidentAuditAttachment> findByIdWithDetails(@Param("id") Long id);

    @EntityGraph(attributePaths = {"cookingAudit", "cookingAudit.recipe", "cookingAudit.user"})
    List<IncidentAuditAttachment> findByIncidentId(Long incidentId);

        @Query("""
                        SELECT a.cookingAudit.id
                        FROM IncidentAuditAttachment a
                        WHERE a.incident.id = :incidentId
                            AND a.cookingAudit.id IN :cookingAuditIds
                        """)
        List<Long> findAttachedCookingAuditIds(@Param("incidentId") Long incidentId,
                                                                                     @Param("cookingAuditIds") List<Long> cookingAuditIds);
}
