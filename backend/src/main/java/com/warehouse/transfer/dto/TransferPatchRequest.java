package com.warehouse.transfer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DISPATCHED ships a draft; CANCELLED cancels it (Managers, with a {@code reason}). Arrivals go to {@code /receipts}. */
public record TransferPatchRequest(@NotNull Status status, @Size(max = 255) String reason) {

    public enum Status { DISPATCHED, CANCELLED }
}
