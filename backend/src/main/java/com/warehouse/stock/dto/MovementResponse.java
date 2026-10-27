package com.warehouse.stock.dto;

import com.warehouse.product.dto.ProductRef;
import com.warehouse.stock.domain.MovementType;
import com.warehouse.stock.domain.ReferenceType;
import com.warehouse.stock.domain.StockMovement;
import com.warehouse.warehouse.dto.WarehouseRef;

import java.time.Instant;

public record MovementResponse(Long id, Instant createdAt, MovementType type, int quantity, int balanceAfter,
                               ProductRef product, WarehouseRef warehouse, String reason,
                               ReferenceType referenceType, Long referenceId, Long reversesId, boolean reversed,
                               String userName) {

    public static MovementResponse from(StockMovement m, boolean reversed) {
        return new MovementResponse(m.getId(), m.getCreatedAt(), m.getType(), m.getQuantity(), m.getBalanceAfter(),
                ProductRef.from(m.getProduct()), WarehouseRef.from(m.getWarehouse()), m.getReason(),
                m.getReferenceType(), m.getReferenceId(), m.getReversesId(), reversed,
                m.getUser() == null ? "System" : m.getUser().getFullName());
    }
}
