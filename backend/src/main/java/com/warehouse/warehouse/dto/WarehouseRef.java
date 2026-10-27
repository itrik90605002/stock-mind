package com.warehouse.warehouse.dto;

import com.warehouse.warehouse.domain.Warehouse;

/** Compact warehouse reference embedded in other responses. */
public record WarehouseRef(Long id, String code, String name) {

    public static WarehouseRef from(Warehouse w) {
        return new WarehouseRef(w.getId(), w.getCode(), w.getName());
    }
}
