package com.warehouse.stock.domain;

import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

/** Composable filters for the stock list. A {@code null} argument means "no filter". */
public final class StockLevelSpecifications {

    private StockLevelSpecifications() {
    }

    public static Specification<StockLevel> inWarehouses(Collection<Long> warehouseIds) {
        return (root, query, cb) -> root.get("warehouse").get("id").in(warehouseIds);
    }

    public static Specification<StockLevel> productMatches(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String pattern = "%" + text.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("product").get("sku")), pattern, '\\'),
                cb.like(cb.lower(root.get("product").get("name")), pattern, '\\'));
    }

    public static Specification<StockLevel> inCategory(String category) {
        return category == null || category.isBlank() ? null
                : (root, query, cb) -> cb.equal(root.get("product").get("category"), category);
    }

    public static Specification<StockLevel> activeProducts() {
        return (root, query, cb) -> cb.isFalse(root.get("product").get("archived"));
    }

    /** Available (on hand - reserved) at or below the reorder point. */
    public static Specification<StockLevel> lowOnly(boolean lowOnly) {
        return !lowOnly ? null : (root, query, cb) -> cb.le(
                cb.diff(root.<Integer>get("onHand"), root.<Integer>get("reserved")), root.<Integer>get("reorderPoint"));
    }
}
