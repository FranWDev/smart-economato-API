package com.economato.inventory.infrastructure.adapter.out.persistence.specification;

import com.economato.inventory.domain.model.Notification;
import com.economato.inventory.domain.model.NotificationType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public class NotificationSpecifications {

    public static Specification<Notification> hasRecipient(Integer recipientId) {
        return (root, query, cb) -> recipientId == null ? null : cb.equal(root.get("recipient").get("id"), recipientId);
    }

    public static Specification<Notification> isNotDeletedByRecipient() {
        return (root, query, cb) -> cb.isFalse(root.get("isDeletedByRecipient"));
    }

    public static Specification<Notification> hasType(NotificationType type) {
        return (root, query, cb) -> type == null ? null : cb.equal(root.get("type"), type);
    }

    public static Specification<Notification> isRead(Boolean read) {
        return (root, query, cb) -> read == null ? null : cb.equal(root.get("isRead"), read);
    }

    public static Specification<Notification> createdAfter(LocalDateTime from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Notification> createdBefore(LocalDateTime to) {
        return (root, query, cb) -> to == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}
