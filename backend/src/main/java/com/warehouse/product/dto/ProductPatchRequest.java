package com.warehouse.product.dto;

import jakarta.validation.Valid;

/** Send {@code details} to edit, {@code archived} to archive or restore, or both. */
public record ProductPatchRequest(@Valid ProductRequest details, Boolean archived) {
}
