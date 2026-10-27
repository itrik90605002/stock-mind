package com.warehouse.dashboard;

import com.warehouse.forecast.dto.ReorderSuggestion;
import com.warehouse.stock.dto.MovementResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record Stats(long activeProducts, long stockUnits, BigDecimal stockValue, long lowStockCount,
                        long openPurchaseOrders, long inTransitTransfers, long movementsToday,
                        long reorderSuggestions) {
    }

    public record Dashboard(Stats stats, List<ReorderSuggestion> urgentReorders, List<TaskGroup> tasks,
                            List<MovementResponse> recentMovements, List<TrendPoint> trend) {
    }

    public enum TaskKind {
        PLACE_ORDER,
        RECEIVE_DELIVERY,
        DISPATCH_TRANSFER,
        RECEIVE_TRANSFER
    }

    public record Task(TaskKind kind, long documentId, String title, String warehouseCode, long units,
                       LocalDate dueDate, Instant since, boolean late) {
    }

    public record NavCounts(long toReorder, long purchaseOrders, long transfers, long late) {
    }

    public record TaskGroup(TaskKind kind, long count, long late, List<Task> items) {
    }

    public record TrendPoint(LocalDate day, long unitsIn, long unitsOut) {
    }

    public record ValuationRow(String warehouseCode, String category, long products, long units, BigDecimal value) {
    }

    public record MoverRow(Long productId, String sku, String name, String category, long unitsOut, long onHand,
                           BigDecimal stockValue) {
    }

    public record Movers(int days, List<MoverRow> fastest, List<MoverRow> slowest) {
    }
}
