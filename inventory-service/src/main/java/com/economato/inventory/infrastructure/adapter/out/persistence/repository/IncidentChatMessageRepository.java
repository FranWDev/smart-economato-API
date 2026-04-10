package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.application.dto.projection.IncidentChatMessageCountProjection;
import com.economato.inventory.domain.model.IncidentChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IncidentChatMessageRepository extends JpaRepository<IncidentChatMessage, Long> {

    @EntityGraph(attributePaths = {"author"})
    List<IncidentChatMessage> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);

    @EntityGraph(attributePaths = {"author"})
    Page<IncidentChatMessage> findByIncidentIdOrderByCreatedAtAsc(Long incidentId, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "incident"})
    Optional<IncidentChatMessage> findById(Long id);

    Optional<IncidentChatMessage> findTopByIncidentIdOrderByIdDesc(Long incidentId);

    long countByIncidentId(Long incidentId);

    @Query("""
            SELECT m.incident.id AS incidentId, COUNT(m) AS messageCount
            FROM IncidentChatMessage m
            WHERE m.incident.id IN :incidentIds
            GROUP BY m.incident.id
            """)
    List<IncidentChatMessageCountProjection> countByIncidentIds(@Param("incidentIds") List<Long> incidentIds);
}
