package com.warehouse.stock.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IssueRequest(@NotNull Long productId, @NotNull Long warehouseId,
                           @Min(1) @Max(1_000_000) int quantity, @Size(max = 255) String reason) {
}
