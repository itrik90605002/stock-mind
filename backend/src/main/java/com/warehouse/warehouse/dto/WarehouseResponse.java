package com.warehouse.warehouse.dto;

import com.warehouse.warehouse.domain.Warehouse;

public record WarehouseResponse(Long id, String code, String name, String address, boolean active,
                                boolean accessible) {

    public static WarehouseResponse from(Warehouse w, boolean accessible) {
        return new WarehouseResponse(w.getId(), w.getCode(), w.getName(), w.getAddress(), w.isActive(), accessible);
    }
}
