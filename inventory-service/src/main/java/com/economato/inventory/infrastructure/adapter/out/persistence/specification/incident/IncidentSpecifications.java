package com.economato.inventory.infrastructure.adapter.out.persistence.specification.incident;

import com.economato.inventory.domain.model.incident.Incident;
import com.economato.inventory.domain.model.incident.IncidentSeverity;
import com.economato.inventory.domain.model.incident.IncidentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public class IncidentSpecifications {

    public static Specification<Incident> hasStatus(IncidentStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Incident> hasSeverity(IncidentSeverity severity) {
        return (root, query, cb) -> severity == null ? null : cb.equal(root.get("severity"), severity);
    }

    public static Specification<Incident> hasIncidentTypeId(Integer incidentTypeId) {
        return (root, query, cb) -> incidentTypeId == null ? null : cb.equal(root.get("incidentType").get("id"), incidentTypeId);
    }

    public static Specification<Incident> hasCreatedById(Integer createdById) {
        return (root, query, cb) -> createdById == null ? null : cb.equal(root.get("createdBy").get("id"), createdById);
    }

    public static Specification<Incident> createdAfter(LocalDateTime from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Incident> createdBefore(LocalDateTime to) {
        return (root, query, cb) -> to == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    public static Specification<Incident> belongsToUser(Integer userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("createdBy").get("id"), userId);
    }

    public static Specification<Incident> belongsToUserOrTeacher(Integer userId, Integer teacherId) {
        return (root, query, cb) -> {
            if (userId == null) {
                return null;
            }
            if (teacherId == null) {
                return cb.equal(root.get("createdBy").get("id"), userId);
            }
            return cb.or(
                    cb.equal(root.get("createdBy").get("id"), userId),
                    cb.equal(root.get("relatedTeacher").get("id"), teacherId)
            );
        };
    }
}
