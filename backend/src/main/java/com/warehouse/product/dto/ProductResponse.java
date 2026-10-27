package com.warehouse.product.dto;

import com.warehouse.product.domain.Product;
import com.warehouse.supplier.domain.Supplier;

import java.math.BigDecimal;

public record ProductResponse(Long id, String sku, String name, String category, String unit, BigDecimal unitCost,
                              String description, Long supplierId, String supplierName, boolean archived) {

    public static ProductResponse from(Product p) {
        Supplier s = p.getSupplier();
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getCategory(), p.getUnit(), p.getUnitCost(),
                p.getDescription(), s == null ? null : s.getId(), s == null ? null : s.getName(), p.isArchived());
    }
}
