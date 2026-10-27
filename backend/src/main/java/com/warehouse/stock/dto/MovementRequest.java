package com.warehouse.stock.dto;

import com.warehouse.stock.domain.MovementType;
import jakarta.validation.constraints.Size;

/**
 * A manual ledger entry: {@code type} ISSUE (quantity > 0) or ADJUSTMENT (signed quantity, reason required),
 * or a reversal of an earlier manual entry when {@code reversesId} is set (reason required).
 */
public record MovementRequest(MovementType type, Long productId, Long warehouseId, Integer quantity,
                              @Size(max = 255) String reason, Long reversesId) {
}
