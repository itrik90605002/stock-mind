package com.warehouse.product.dto;

import com.warehouse.product.domain.Product;

public record ProductRef(Long id, String sku, String name, String unit) {

    public static ProductRef from(Product p) {
        return new ProductRef(p.getId(), p.getSku(), p.getName(), p.getUnit());
    }
}
