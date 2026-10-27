package com.warehouse.transfer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateTransferRequest(@NotNull Long sourceWarehouseId, @NotNull Long destinationWarehouseId,
                                    @Size(max = 500) String note,
                                    @NotNull @Size(min = 1, max = 100) List<@Valid TransferLineRequest> lines) {
}
