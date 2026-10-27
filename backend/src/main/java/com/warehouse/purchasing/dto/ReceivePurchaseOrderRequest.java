package com.warehouse.purchasing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReceivePurchaseOrderRequest(@NotNull @Size(min = 1, max = 200) List<@Valid Line> lines) {

    public record Line(@NotNull Long lineId, @Min(1) @Max(1_000_000) int quantity) {
    }
}
