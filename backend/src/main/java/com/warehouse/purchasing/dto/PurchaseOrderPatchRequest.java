package com.warehouse.purchasing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Send {@code details} to revise a draft, or {@code status}: ORDERED places it, CANCELLED cancels it and
 * CLOSED accepts a partial delivery as final (both need a {@code reason}). Deliveries go to {@code /receipts}.
 */
public record PurchaseOrderPatchRequest(@Valid PurchaseOrderRequest details, Status status,
                                        @Size(max = 255) String reason) {

    public enum Status { ORDERED, CANCELLED, CLOSED }
}
