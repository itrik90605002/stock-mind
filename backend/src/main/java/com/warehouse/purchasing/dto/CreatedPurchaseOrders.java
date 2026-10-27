package com.warehouse.purchasing.dto;

import java.util.List;

public record CreatedPurchaseOrders(List<PurchaseOrderResponse> orders) {
}
