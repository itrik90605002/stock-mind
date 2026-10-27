package com.warehouse.purchasing.domain;

import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

public final class PurchaseOrderSpecifications {

    private PurchaseOrderSpecifications() {
    }

    public static Specification<PurchaseOrder> inWarehouses(Collection<Long> warehouseIds) {
        return (root, query, cb) -> root.get("warehouse").get("id").in(warehouseIds);
    }

    public static Specification<PurchaseOrder> withStatus(PurchaseOrderStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<PurchaseOrder> openOnly(boolean openOnly) {
        return !openOnly ? null : (root, query, cb) -> root.get("status").in(PurchaseOrderStatus.OPEN);
    }

    public static Specification<PurchaseOrder> fromSupplier(Long supplierId) {
        return supplierId == null ? null : (root, query, cb) -> cb.equal(root.get("supplier").get("id"), supplierId);
    }
}
