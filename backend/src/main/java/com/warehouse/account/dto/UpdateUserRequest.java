package com.warehouse.account.dto;

import com.warehouse.account.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateUserRequest(@NotBlank @Size(max = 120) String fullName, @NotNull Role role,
                                @NotNull Set<Long> warehouseIds) {
}
