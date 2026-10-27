package com.warehouse.stock.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Count correction, damage or loss. {@code quantityChange} is signed and must not be zero. */
public record AdjustRequest(@NotNull Long productId, @NotNull Long warehouseId,
                            @Min(-1_000_000) @Max(1_000_000) int quantityChange,
                            @NotBlank @Size(max = 255) String reason) {

    @AssertTrue(message = "quantityChange must not be zero")
    public boolean isNonZero() {
        return quantityChange != 0;
    }
}
