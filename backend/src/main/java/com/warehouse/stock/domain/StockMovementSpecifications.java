package com.warehouse.stock.domain;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collection;

public final class StockMovementSpecifications {

    private StockMovementSpecifications() {
    }

    public static Specification<StockMovement> inWarehouses(Collection<Long> warehouseIds) {
        return (root, query, cb) -> root.get("warehouse").get("id").in(warehouseIds);
    }

    public static Specification<StockMovement> forProduct(Long productId) {
        return productId == null ? null : (root, query, cb) -> cb.equal(root.get("product").get("id"), productId);
    }

    public static Specification<StockMovement> ofType(MovementType type) {
        return type == null ? null : (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<StockMovement> referencing(ReferenceType type, Long id) {
        if (type == null) {
            return null;
        }
        return (root, query, cb) -> id == null ? cb.equal(root.get("referenceType"), type)
                : cb.and(cb.equal(root.get("referenceType"), type), cb.equal(root.get("referenceId"), id));
    }

    public static Specification<StockMovement> from(Instant from) {
        return from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<StockMovement> before(Instant to) {
        return to == null ? null : (root, query, cb) -> cb.lessThan(root.get("createdAt"), to);
    }
}
