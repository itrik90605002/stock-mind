package com.warehouse.transfer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Quantities actually received. Lines that are not listed are taken as received in full;
 * an empty list means "everything arrived".
 */
public record ReceiveTransferRequest(List<@Valid Line> lines) {

    public record Line(@NotNull Long lineId, @Min(0) @Max(1_000_000) int receivedQuantity) {
    }
}
