package com.economato.inventory.infrastructure.adapter.out.persistence.specification.shared;

import com.economato.inventory.domain.model.shared.InventoryAudit;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public class InventoryAuditSpecifications {

    public static Specification<InventoryAudit> hasMovementType(String type) {
        return (root, query, cb) -> type == null ? null : cb.equal(root.get("movementType"), type);
    }

    public static Specification<InventoryAudit> hasMovementDateAfter(LocalDateTime start) {
        return (root, query, cb) -> start == null ? null : cb.greaterThanOrEqualTo(root.get("movementDate"), start);
    }

    public static Specification<InventoryAudit> hasMovementDateBefore(LocalDateTime end) {
        return (root, query, cb) -> end == null ? null : cb.lessThanOrEqualTo(root.get("movementDate"), end);
    }

    public static Specification<InventoryAudit> productNameContains(String productName) {
        return (root, query, cb) -> {
            if (productName == null || productName.isBlank()) {
                return null;
            }
            return cb.like(cb.lower(root.get("product").get("name")), "%" + productName.toLowerCase() + "%");
        };
    }
}
