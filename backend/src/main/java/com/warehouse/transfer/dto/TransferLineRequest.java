package com.warehouse.transfer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TransferLineRequest(@NotNull Long productId, @Min(1) @Max(1_000_000) int quantity) {
}
