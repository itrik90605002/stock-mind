package com.warehouse.purchasing.dto;

import java.util.List;

/** Result of creating several draft orders at once (e.g. from reorder suggestions). */
public record CreatedPurchaseOrders(List<PurchaseOrderResponse> orders) {
}
