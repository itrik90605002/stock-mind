package com.warehouse.purchasing.domain;

/**
 * Purchase order lifecycle:
 * {@code DRAFT -> ORDERED -> PARTIALLY_RECEIVED -> RECEIVED}; {@code DRAFT/ORDERED -> CANCELLED}
 * while nothing has been received; a partially received order can be closed short (-> RECEIVED).
 */
public enum PurchaseOrderStatus {
    DRAFT,
    ORDERED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELLED;

    /** Orders whose remaining quantities are still expected to arrive. */
    public static final java.util.Set<PurchaseOrderStatus> OPEN_FOR_RECEIPT =
            java.util.EnumSet.of(ORDERED, PARTIALLY_RECEIVED);

    /** Orders that still block deactivating their warehouse. */
    public static final java.util.Set<PurchaseOrderStatus> OPEN = java.util.EnumSet.of(DRAFT, ORDERED, PARTIALLY_RECEIVED);
}
