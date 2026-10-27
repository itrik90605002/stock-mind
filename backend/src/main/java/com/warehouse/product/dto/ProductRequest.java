package com.warehouse.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Create/update body. The SKU cannot change after creation (it is referenced by the ledger). */
public record ProductRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{2,40}", message = "2-40 letters, digits, '.', '_' or '-'") String sku,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 80) String category,
        @NotBlank @Size(max = 20) String unit,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitCost,
        @Size(max = 2000) String description,
        Long supplierId) {
}
