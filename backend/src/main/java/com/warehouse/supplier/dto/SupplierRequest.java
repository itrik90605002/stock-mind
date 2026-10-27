package com.warehouse.supplier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierRequest(
        @NotBlank @Size(max = 120) String name,
        @Email @Size(max = 255) String contactEmail,
        @Size(max = 40) String phone,
        @Min(0) @Max(365) int leadTimeDays) {
}
