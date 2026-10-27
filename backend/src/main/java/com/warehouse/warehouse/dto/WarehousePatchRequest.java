package com.warehouse.warehouse.dto;

import jakarta.validation.Valid;

/** Send {@code details} to edit, {@code active} to activate or deactivate, or both. */
public record WarehousePatchRequest(@Valid WarehouseRequest details, Boolean active) {
}
