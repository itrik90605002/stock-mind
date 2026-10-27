package com.warehouse.stock.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockSettingsRequest(@NotNull Long productId, @NotNull Long warehouseId,
                                   @Min(0) @Max(1_000_000) int reorderPoint,
                                   @Min(0) @Max(365) Integer leadTimeDays) {
}
