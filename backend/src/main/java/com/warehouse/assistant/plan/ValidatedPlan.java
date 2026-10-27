package com.warehouse.assistant.plan;

import com.warehouse.stock.domain.MovementType;

import java.time.LocalDate;
import java.util.List;

public record ValidatedPlan(QueryIntent intent, List<Long> warehouseIds, String warehouseLabel,
                            List<Long> productIds, String productLabel, String category, Long supplierId,
                            String supplierLabel, MovementType movementType, List<String> statuses,
                            String statusLabel, LocalDate from, LocalDate to, int horizonDays, Direction direction,
                            SortBy sortBy, int limit) {

    public enum Direction { FAST, SLOW }

    public enum SortBy { RECENT, LARGEST }
}
