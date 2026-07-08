package com.economato.inventory.infrastructure.adapter.out.persistence.repository.incident;
import com.economato.inventory.domain.model.incident.IncidentSeverity;

import com.economato.inventory.domain.model.incident.Incident;
import com.economato.inventory.domain.model.incident.IncidentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    @Override
    @EntityGraph(attributePaths = {"incidentType", "createdBy", "relatedTeacher", "openedBy", "closedBy"})
    Page<Incident> findAll(Specification<Incident> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"incidentType", "createdBy", "relatedTeacher", "openedBy", "closedBy"})
    Optional<Incident> findById(Long id);

    @Query("""
            SELECT i FROM Incident i
            ORDER BY
                CASE i.severity
                    WHEN IncidentSeverity.ALTA THEN 3
                    WHEN IncidentSeverity.MEDIA THEN 2
                    WHEN IncidentSeverity.BAJA THEN 1
                    ELSE 0
                END DESC,
                i.createdAt DESC
            """)
    Page<Incident> findAllByOrderBySeverityDescCreatedAtDesc(Pageable pageable);

    Page<Incident> findByStatus(IncidentStatus status, Pageable pageable);

    Page<Incident> findByCreatedById(Integer userId, Pageable pageable);

    Page<Incident> findByCreatedByIdOrRelatedTeacherId(Integer userId, Integer teacherId, Pageable pageable);

    @EntityGraph(attributePaths = {"incidentType", "createdBy", "relatedTeacher", "openedBy", "closedBy"})
    @Query("SELECT i FROM Incident i WHERE i.id = :id")
    Optional<Incident> findDetailById(@Param("id") Long id);
}