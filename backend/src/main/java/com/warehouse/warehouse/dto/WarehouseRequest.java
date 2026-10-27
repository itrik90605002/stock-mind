package com.warehouse.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WarehouseRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{2,20}", message = "2-20 letters, digits or dashes") String code,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 255) String address) {
}
