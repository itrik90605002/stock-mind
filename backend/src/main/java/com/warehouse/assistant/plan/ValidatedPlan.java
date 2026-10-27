package com.warehouse.assistant.plan;

import com.warehouse.stock.domain.MovementType;

import java.time.LocalDate;
import java.util.List;

/**
 * A plan that passed validation: enums parsed, names resolved to ids, dates bounded, warehouse
 * scope intersected with the user's permissions and row limit capped. Only this type reaches the
 * query executor.
 *
 * @param warehouseIds  warehouses the query may read (never empty)
 * @param productIds    matched products, or empty for "all products"
 * @param statuses      status names to include, or empty for all
 * @param from          inclusive start date, or null
 * @param to            inclusive end date, or null
 */
public record ValidatedPlan(QueryIntent intent, List<Long> warehouseIds, String warehouseLabel,
                            List<Long> productIds, String productLabel, String category, Long supplierId,
                            String supplierLabel, MovementType movementType, List<String> statuses,
                            String statusLabel, LocalDate from, LocalDate to, int horizonDays, Direction direction,
                            SortBy sortBy, int limit) {

    public enum Direction { FAST, SLOW }

    public enum SortBy { RECENT, LARGEST }
}
