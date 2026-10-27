package com.warehouse.stock.dto;

import java.time.Instant;
import java.util.List;

public record ConsistencyReport(Instant checkedAt, int levelsChecked, List<Mismatch> mismatches) {

    public boolean consistent() {
        return mismatches.isEmpty();
    }

    public record Mismatch(Long productId, String sku, Long warehouseId, String warehouseCode, int recordedOnHand,
                           int ledgerOnHand, int recordedReserved, int expectedReserved) {
    }
}
