package com.warehouse.stock.domain;

public enum MovementType {
    RECEIPT,
    ISSUE,
    ADJUSTMENT,
    TRANSFER_OUT,
    TRANSFER_IN;

    public static final java.util.Set<MovementType> OUTBOUND_USAGE = java.util.EnumSet.of(ISSUE, TRANSFER_OUT);
}
