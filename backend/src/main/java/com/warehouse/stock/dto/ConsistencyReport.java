package com.warehouse.stock.dto;

import java.time.Instant;
import java.util.List;

/**
 * Result of rebuilding stock from the ledger.
 *
 * @param levelsChecked product/warehouse pairs compared
 * @param mismatches    pairs whose stored quantities differ from what the ledger and open transfers imply
 */
public record ConsistencyReport(Instant checkedAt, int levelsChecked, List<Mismatch> mismatches) {

    public boolean consistent() {
        return mismatches.isEmpty();
    }

    /**
     * @param recordedOnHand   on_hand stored in stock_levels
     * @param ledgerOnHand     sum of all ledger entries for the pair
     * @param recordedReserved reserved stored in stock_levels
     * @param expectedReserved quantity on draft transfers leaving this warehouse
     */
    public record Mismatch(Long productId, String sku, Long warehouseId, String warehouseCode, int recordedOnHand,
                           int ledgerOnHand, int recordedReserved, int expectedReserved) {
    }
}
