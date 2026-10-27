package com.warehouse.dashboard;

import com.warehouse.forecast.dto.ReorderSuggestion;
import com.warehouse.stock.dto.MovementResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Response records for the dashboard and reports. */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record Stats(long activeProducts, long stockUnits, BigDecimal stockValue, long lowStockCount,
                        long openPurchaseOrders, long inTransitTransfers, long movementsToday,
                        long reorderSuggestions) {
    }

    /**
     * The home page: what to do next ({@code urgentReorders} and {@code tasks}), then how things stand.
     *
     * @param tasks one group per step the user is allowed to perform, in journey order, including empty groups
     */
    public record Dashboard(Stats stats, List<ReorderSuggestion> urgentReorders, List<TaskGroup> tasks,
                            List<MovementResponse> recentMovements, List<TrendPoint> trend) {
    }

    /** The next step a purchase order or transfer is waiting for. Declared in the order of the stock journey. */
    public enum TaskKind {
        /** A draft purchase order to review and place with the supplier (Managers). */
        PLACE_ORDER,
        /** A placed purchase order whose goods are still expected. */
        RECEIVE_DELIVERY,
        /** A draft transfer leaving one of the user's warehouses. */
        DISPATCH_TRANSFER,
        /** A dispatched transfer arriving at one of the user's warehouses. */
        RECEIVE_TRANSFER
    }

    /**
     * One document waiting for its next step.
     *
     * @param title         supplier name for purchase orders, "SRC → DST" for transfers
     * @param warehouseCode where the step happens
     * @param units         units still to place, receive or dispatch
     * @param dueDate       expected delivery date (purchase orders only)
     * @param since         when the document reached its current step
     */
    public record Task(TaskKind kind, long documentId, String title, String warehouseCode, long units,
                       LocalDate dueDate, Instant since, boolean late) {
    }

    /**
     * Counts for the sidebar badges, so the to-do list stays visible on every page.
     *
     * @param toReorder      reorder suggestions (Managers and Admins only; 0 for Clerks, who cannot order)
     * @param purchaseOrders drafts to place plus deliveries to receive
     * @param transfers      transfers to dispatch plus transfers arriving
     * @param late           deliveries past their expected date
     */
    public record NavCounts(long toReorder, long purchaseOrders, long transfers, long late) {
    }

    /** The most pressing tasks of one kind, plus how many there are in total. */
    public record TaskGroup(TaskKind kind, long count, long late, List<Task> items) {
    }

    /** Units in and out per day, for the activity chart. */
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
