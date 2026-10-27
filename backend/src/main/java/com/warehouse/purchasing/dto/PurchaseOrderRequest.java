package com.warehouse.purchasing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Create/revise a draft purchase order. A line without {@code unitCost} uses the product's unit cost. */
public record PurchaseOrderRequest(@NotNull Long supplierId, @NotNull Long warehouseId, LocalDate expectedDate,
                                   @Size(max = 500) String note,
                                   @NotNull @Size(min = 1, max = 200) List<@Valid Line> lines) {

    public record Line(@NotNull Long productId, @Min(1) @Max(1_000_000) int quantity,
                       @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitCost) {
    }
}
