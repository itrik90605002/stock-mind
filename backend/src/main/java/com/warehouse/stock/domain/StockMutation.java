package com.warehouse.stock.domain;

import com.warehouse.account.domain.User;

import java.time.Instant;

/**
 * The only way to change a {@link StockLevel}: each method mutates the level and, when on-hand
 * changes, returns the matching (unsaved) ledger entry. Lives in the domain package so the
 * package-private mutators stay hidden from the rest of the code base.
 */
public final class StockMutation {

    private StockMutation() {
    }

    public static StockMovement change(StockLevel level, MovementType type, int delta, String reason,
                                       ReferenceType referenceType, Long referenceId, Long reversesId,
                                       User user, Instant now) {
        level.changeOnHand(delta, now);
        return new StockMovement(level.getProduct(), level.getWarehouse(), type, delta, level.getOnHand(), reason,
                referenceType, referenceId, reversesId, user, now);
    }

    public static StockMovement consumeReservation(StockLevel level, int quantity, String reason, Long transferId,
                                                   User user, Instant now) {
        level.consumeReservation(quantity, now);
        return new StockMovement(level.getProduct(), level.getWarehouse(), MovementType.TRANSFER_OUT, -quantity,
                level.getOnHand(), reason, ReferenceType.TRANSFER, transferId, null, user, now);
    }

    public static void reserve(StockLevel level, int quantity, Instant now) {
        level.reserve(quantity, now);
    }

    public static void release(StockLevel level, int quantity, Instant now) {
        level.releaseReservation(quantity, now);
    }
}
