package com.warehouse.stock.domain;

/** Kinds of ledger entries. The sign of the quantity is implied for all but {@link #ADJUSTMENT}. */
public enum MovementType {
    /** Goods received from a supplier (+). */
    RECEIPT,
    /** Goods leaving the business: picked for an order, consumed, sold (-). */
    ISSUE,
    /** Count correction, damage, loss, or reversal of an earlier entry (+/-). */
    ADJUSTMENT,
    /** Goods dispatched to another warehouse (-). */
    TRANSFER_OUT,
    /** Goods arriving from another warehouse (+). */
    TRANSFER_IN;

    /** Movement types that represent consumption, used for usage statistics and forecasts. */
    public static final java.util.Set<MovementType> OUTBOUND_USAGE = java.util.EnumSet.of(ISSUE, TRANSFER_OUT);
}
