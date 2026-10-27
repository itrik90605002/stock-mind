package com.warehouse.transfer.domain;

import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

public final class TransferSpecifications {

    private TransferSpecifications() {
    }

    public static Specification<Transfer> involving(Collection<Long> warehouseIds) {
        return (root, query, cb) -> cb.or(
                root.get("source").get("id").in(warehouseIds),
                root.get("destination").get("id").in(warehouseIds));
    }

    public static Specification<Transfer> withStatus(TransferStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
