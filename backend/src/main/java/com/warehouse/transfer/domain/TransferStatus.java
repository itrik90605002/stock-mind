package com.warehouse.transfer.domain;

/**
 * Transfer lifecycle: {@code DRAFT -> DISPATCHED -> RECEIVED}, or {@code DRAFT -> CANCELLED}.
 * A draft reserves stock at the source; dispatch ships it; receipt books it in at the destination.
 */
public enum TransferStatus {
    DRAFT,
    DISPATCHED,
    RECEIVED,
    CANCELLED
}
