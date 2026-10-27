package com.warehouse.transfer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReceiveTransferRequest(List<@Valid Line> lines) {

    public record Line(@NotNull Long lineId, @Min(0) @Max(1_000_000) int receivedQuantity) {
    }
}
