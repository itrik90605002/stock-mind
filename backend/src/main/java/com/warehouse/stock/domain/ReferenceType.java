package com.warehouse.stock.domain;

/** The document that caused a ledger entry. */
public enum ReferenceType {
    PURCHASE_ORDER,
    TRANSFER,
    /** Recorded directly by a user (issue, adjustment, reversal). */
    MANUAL,
    /** Opening balance loaded when the system was set up. */
    OPENING
}
