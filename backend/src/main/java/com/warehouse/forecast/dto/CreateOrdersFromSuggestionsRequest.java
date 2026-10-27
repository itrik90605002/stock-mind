package com.warehouse.forecast.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrdersFromSuggestionsRequest(@NotNull @Size(min = 1, max = 200) List<@Valid Item> items) {

    public record Item(@NotNull Long productId, @NotNull Long warehouseId, @Min(1) @Max(1_000_000) int quantity) {
    }
}
