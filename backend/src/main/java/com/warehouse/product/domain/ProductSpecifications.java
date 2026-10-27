package com.warehouse.product.domain;

import org.springframework.data.jpa.domain.Specification;

/** Composable filters for the product list. */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    /** Case-insensitive match on SKU or name; no-op when the text is blank. */
    public static Specification<Product> matches(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String pattern = "%" + text.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("sku")), pattern, '\\'),
                cb.like(cb.lower(root.get("name")), pattern, '\\'));
    }

    public static Specification<Product> inCategory(String category) {
        return category == null || category.isBlank() ? null
                : (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    public static Specification<Product> fromSupplier(Long supplierId) {
        return supplierId == null ? null
                : (root, query, cb) -> cb.equal(root.get("supplier").get("id"), supplierId);
    }

    public static Specification<Product> archived(boolean includeArchived) {
        return includeArchived ? null : (root, query, cb) -> cb.isFalse(root.get("archived"));
    }
}
