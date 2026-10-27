package com.warehouse.product.dto;

import com.warehouse.product.domain.Product;

/** Compact product reference embedded in other responses. */
public record ProductRef(Long id, String sku, String name, String unit) {

    public static ProductRef from(Product p) {
        return new ProductRef(p.getId(), p.getSku(), p.getName(), p.getUnit());
    }
}
