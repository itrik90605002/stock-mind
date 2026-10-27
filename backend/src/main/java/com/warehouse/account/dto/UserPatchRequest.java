package com.warehouse.account.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** Send {@code details} to edit, {@code active} to activate or deactivate (with a {@code reason}), or both. */
public record UserPatchRequest(@Valid UpdateUserRequest details, Boolean active, @Size(max = 255) String reason) {
}
