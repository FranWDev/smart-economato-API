package com.economato.inventory.infrastructure.adapter.out.persistence.specification.order;

import com.economato.inventory.domain.model.order.Order;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public class OrderSpecifications {

    public static Specification<Order> hasOrderId(Integer orderId) {
        return (root, query, cb) -> orderId == null ? null : cb.equal(root.get("id"), orderId);
    }

    public static Specification<Order> hasUserId(Integer userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Order> hasSupplierId(Integer supplierId) {
        return (root, query, cb) -> supplierId == null ? null : cb.equal(root.get("supplier").get("id"), supplierId);
    }

    public static Specification<Order> hasOrderDateAfter(LocalDateTime startDate) {
        return (root, query, cb) -> startDate == null ? null : cb.greaterThanOrEqualTo(root.get("orderDate"), startDate);
    }

    public static Specification<Order> hasOrderDateBefore(LocalDateTime endDate) {
        return (root, query, cb) -> endDate == null ? null : cb.lessThanOrEqualTo(root.get("orderDate"), endDate);
    }
}