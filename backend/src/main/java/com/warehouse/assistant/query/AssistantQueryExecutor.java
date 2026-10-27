package com.warehouse.assistant.query;

import com.warehouse.assistant.plan.ValidatedPlan;
import com.warehouse.forecast.domain.StockForecast;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a {@link ValidatedPlan} as one of a fixed set of prewritten SQL queries.
 *
 * <p>Safety properties:
 * <ul>
 *   <li>SQL text is built only from constants in this class; optional filters append constant
 *       fragments. Every value (ids, dates, limits) is a bind parameter.</li>
 *   <li>Every query is restricted to {@code plan.warehouseIds()}, which the validator has already
 *       intersected with the user's permissions.</li>
 *   <li>Execution happens in a read-only transaction with a statement timeout, so even a bug here
 *       cannot modify data or run away with the database.</li>
 *   <li>Row count is capped by the plan's limit; one extra row is fetched to detect truncation.</li>
 * </ul>
 */
@Component
public class AssistantQueryExecutor {

    private static final int STATEMENT_TIMEOUT_MS = 5_000;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate readOnlyTx;
    private final Clock clock;

    public AssistantQueryExecutor(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                                  Clock clock) {
        this.jdbc = jdbc;
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
        this.clock = clock;
    }

    public QueryResult run(ValidatedPlan plan) {
        return readOnlyTx.execute(status -> {
            jdbc.getJdbcTemplate().execute("SET LOCAL statement_timeout = " + STATEMENT_TIMEOUT_MS);
            return switch (plan.intent()) {
                case STOCK_LOOKUP -> stock(plan, false);
                case LOW_STOCK -> stock(plan, true);
                case STOCKOUT_FORECAST -> stockoutForecast(plan);
                case MOVEMENT_HISTORY -> movements(plan);
                case TOP_MOVERS -> movers(plan);
                case PURCHASE_ORDER_STATUS -> purchaseOrders(plan);
                case TRANSFER_STATUS -> transfers(plan);
                case REORDER_SUGGESTIONS -> reorderSuggestions(plan);
                case STOCK_VALUE -> stockValue(plan);
                case UNSUPPORTED -> throw new IllegalStateException("Unsupported plans never reach the executor");
            };
        });
    }

    // ------------------------------------------------------------------ intents

    private QueryResult stock(ValidatedPlan plan, boolean lowOnly) {
        Query q = new Query("""
                SELECT p.sku, p.name, p.category, w.code AS warehouse, s.on_hand, s.reserved,
                       s.on_hand - s.reserved AS available, s.reorder_point, p.unit
                FROM stock_levels s
                JOIN products p ON p.id = s.product_id
                JOIN warehouses w ON w.id = s.warehouse_id
                WHERE s.warehouse_id IN (:warehouseIds) AND p.archived = FALSE
                """, plan);
        q.productFilter("p.id").categoryFilter("p.category").supplierFilter("p.supplier_id");
        if (lowOnly) {
            q.sql.append(" AND s.on_hand - s.reserved <= s.reorder_point");
            q.sql.append(" ORDER BY (s.on_hand - s.reserved - s.reorder_point), p.sku");
        } else {
            q.sql.append(" ORDER BY p.sku, w.code");
        }
        List<Column> columns = List.of(Column.text("sku", "SKU"), Column.text("name", "Product"),
                Column.text("category", "Category"), Column.text("warehouse", "Warehouse"),
                Column.number("on_hand", "On hand"), Column.number("reserved", "Reserved"),
                Column.number("available", "Available"), Column.number("reorder_point", "Reorder point"),
                Column.text("unit", "Unit"));
        Rows rows = q.fetch();
        String summary = lowOnly
                ? count(rows, "item", "items") + " at or below the reorder point in " + plan.warehouseLabel() + "."
                : "Stock for " + count(rows, "product/warehouse pair", "product/warehouse pairs") + ".";
        return rows.result(columns, summary);
    }

    private QueryResult stockoutForecast(ValidatedPlan plan) {
        Query q = new Query("""
                SELECT p.sku, p.name, w.code AS warehouse, s.on_hand - s.reserved AS available, p.unit,
                       f.avg_daily_usage, ROUND((s.on_hand - s.reserved) / f.avg_daily_usage, 1) AS days_left,
                       f.incoming, f.lead_time_days, f.suggested_qty
                FROM stock_forecasts f
                JOIN stock_levels s ON s.product_id = f.product_id AND s.warehouse_id = f.warehouse_id
                JOIN products p ON p.id = f.product_id
                JOIN warehouses w ON w.id = f.warehouse_id
                WHERE f.warehouse_id IN (:warehouseIds) AND p.archived = FALSE AND f.avg_daily_usage > 0
                  AND (s.on_hand - s.reserved) / f.avg_daily_usage <= :horizon
                """, plan);
        q.params.addValue("horizon", plan.horizonDays());
        q.productFilter("p.id").categoryFilter("p.category").supplierFilter("p.supplier_id");
        q.sql.append(" ORDER BY days_left, p.sku");
        Rows rows = q.fetch();
        LocalDate today = LocalDate.now(clock);
        for (Map<String, Object> row : rows.list) {
            double daysLeft = ((BigDecimal) row.get("days_left")).doubleValue();
            row.put("runs_out_on", today.plusDays((long) Math.floor(daysLeft)));
            row.put("avg_daily_usage", ((BigDecimal) row.get("avg_daily_usage")).setScale(1, RoundingMode.HALF_UP));
        }
        List<Column> columns = List.of(Column.text("sku", "SKU"), Column.text("name", "Product"),
                Column.text("warehouse", "Warehouse"), Column.number("available", "Available"),
                Column.decimal("avg_daily_usage", "Avg/day"), Column.decimal("days_left", "Days left"),
                Column.date("runs_out_on", "Runs out ~"), Column.number("incoming", "On order"),
                Column.number("lead_time_days", "Lead time (d)"), Column.number("suggested_qty", "Suggested order"));
        long uncovered = rows.list.stream().filter(r -> ((Number) r.get("incoming")).intValue() == 0).count();
        String summary = rows.list.isEmpty()
                ? "Nothing in " + plan.warehouseLabel() + " is expected to run out within " + plan.horizonDays() + " days at current usage."
                : count(rows, "item", "items") + " will run out within " + plan.horizonDays()
                + " days at current usage; " + uncovered + " of them have nothing on order.";
        return rows.result(columns, summary);
    }

    private QueryResult movements(ValidatedPlan plan) {
        Query q = new Query("""
                SELECT m.id, m.created_at, m.type, p.sku, p.name, w.code AS warehouse, m.quantity, m.balance_after,
                       m.reason, COALESCE(u.full_name, 'System') AS recorded_by
                FROM stock_movements m
                JOIN products p ON p.id = m.product_id
                JOIN warehouses w ON w.id = m.warehouse_id
                LEFT JOIN users u ON u.id = m.user_id
                WHERE m.warehouse_id IN (:warehouseIds)
                """, plan);
        q.dateFilter("m.created_at").productFilter("p.id").categoryFilter("p.category").supplierFilter("p.supplier_id");
        if (plan.movementType() != null) {
            q.sql.append(" AND m.type = :movementType");
            q.params.addValue("movementType", plan.movementType().name());
        }
        q.sql.append(plan.sortBy() == ValidatedPlan.SortBy.LARGEST
                ? " ORDER BY ABS(m.quantity) DESC, m.created_at DESC" : " ORDER BY m.created_at DESC, m.id DESC");
        List<Column> columns = List.of(Column.datetime("created_at", "When"), Column.status("type", "Type"),
                Column.text("sku", "SKU"), Column.text("name", "Product"), Column.text("warehouse", "Warehouse"),
                Column.number("quantity", "Qty"), Column.number("balance_after", "Balance"),
                Column.text("reason", "Reason"), Column.text("recorded_by", "By"));
        Rows rows = q.fetch();
        return rows.result(columns, count(rows, "movement", "movements") + " between " + plan.from() + " and "
                + plan.to() + (rows.truncated ? " (showing the first " + plan.limit() + ")." : "."));
    }

    private QueryResult movers(ValidatedPlan plan) {
        Query q = new Query("""
                WITH usage AS (
                    SELECT product_id, SUM(-quantity) AS units, COUNT(*) AS movements
                    FROM stock_movements
                    WHERE type IN ('ISSUE', 'TRANSFER_OUT') AND warehouse_id IN (:warehouseIds)
                      AND created_at >= :fromTs AND created_at < :toTs
                    GROUP BY product_id
                ), stock AS (
                    SELECT product_id, SUM(on_hand) AS on_hand FROM stock_levels
                    WHERE warehouse_id IN (:warehouseIds) GROUP BY product_id
                )
                SELECT p.sku, p.name, p.category, COALESCE(u.units, 0) AS units_out, COALESCE(u.movements, 0) AS movements,
                       st.on_hand, st.on_hand * p.unit_cost AS stock_value
                FROM stock st
                JOIN products p ON p.id = st.product_id
                LEFT JOIN usage u ON u.product_id = st.product_id
                WHERE p.archived = FALSE
                """, plan);
        q.dateParams().productFilter("p.id").categoryFilter("p.category").supplierFilter("p.supplier_id");
        q.sql.append(plan.direction() == ValidatedPlan.Direction.FAST
                ? " ORDER BY units_out DESC, p.sku" : " ORDER BY units_out ASC, stock_value DESC, p.sku");
        List<Column> columns = List.of(Column.text("sku", "SKU"), Column.text("name", "Product"),
                Column.text("category", "Category"), Column.number("units_out", "Units out"),
                Column.number("movements", "Movements"), Column.number("on_hand", "On hand"),
                Column.money("stock_value", "Stock value"));
        Rows rows = q.fetch();
        String which = plan.direction() == ValidatedPlan.Direction.FAST ? "Fastest" : "Slowest";
        return rows.result(columns, which + " moving products by units out, " + plan.from() + " to " + plan.to() + ".");
    }

    private QueryResult purchaseOrders(ValidatedPlan plan) {
        Query q = new Query("""
                SELECT o.id, o.status, s.name AS supplier, w.code AS warehouse, o.expected_date, o.created_at,
                       SUM(l.quantity) AS units, SUM(l.quantity - l.received_quantity) AS remaining,
                       SUM(l.quantity * l.unit_cost) AS value
                FROM purchase_orders o
                JOIN suppliers s ON s.id = o.supplier_id
                JOIN warehouses w ON w.id = o.warehouse_id
                JOIN purchase_order_lines l ON l.purchase_order_id = o.id
                WHERE o.warehouse_id IN (:warehouseIds)
                """, plan);
        q.statusFilter("o.status").dateFilter("o.created_at").supplierFilter("o.supplier_id");
        if (!plan.productIds().isEmpty()) {
            q.sql.append(" AND EXISTS (SELECT 1 FROM purchase_order_lines x WHERE x.purchase_order_id = o.id AND x.product_id IN (:productIds))");
            q.params.addValue("productIds", plan.productIds());
        }
        q.sql.append(" GROUP BY o.id, s.name, w.code ORDER BY o.created_at DESC");
        Rows rows = q.fetch();
        LocalDate today = LocalDate.now(clock);
        long late = 0;
        for (Map<String, Object> row : rows.list) {
            boolean open = "ORDERED".equals(row.get("status")) || "PARTIALLY_RECEIVED".equals(row.get("status"));
            boolean isLate = open && row.get("expected_date") instanceof LocalDate d && d.isBefore(today);
            row.put("late", isLate ? "Late" : "");
            late += isLate ? 1 : 0;
            if (!open) {
                row.put("remaining", 0);
            }
        }
        List<Column> columns = List.of(Column.text("id", "PO #"), Column.status("status", "Status"),
                Column.text("supplier", "Supplier"), Column.text("warehouse", "Warehouse"),
                Column.date("expected_date", "Expected"), Column.text("late", ""), Column.number("units", "Units"),
                Column.number("remaining", "Still expected"), Column.money("value", "Value"),
                Column.datetime("created_at", "Created"));
        return rows.result(columns, count(rows, "purchase order", "purchase orders")
                + (late > 0 ? "; " + late + " past the expected date." : "."));
    }

    private QueryResult transfers(ValidatedPlan plan) {
        Query q = new Query("""
                SELECT t.id, t.status, ws.code AS source, wd.code AS destination, t.created_at, t.dispatched_at,
                       t.received_at, SUM(l.quantity) AS units, SUM(COALESCE(l.quantity - l.received_quantity, 0)) AS shortfall
                FROM transfers t
                JOIN warehouses ws ON ws.id = t.source_warehouse_id
                JOIN warehouses wd ON wd.id = t.destination_warehouse_id
                JOIN transfer_lines l ON l.transfer_id = t.id
                WHERE (t.source_warehouse_id IN (:warehouseIds) OR t.destination_warehouse_id IN (:warehouseIds))
                """, plan);
        q.statusFilter("t.status").dateFilter("t.created_at");
        if (!plan.productIds().isEmpty()) {
            q.sql.append(" AND EXISTS (SELECT 1 FROM transfer_lines x WHERE x.transfer_id = t.id AND x.product_id IN (:productIds))");
            q.params.addValue("productIds", plan.productIds());
        }
        q.sql.append(" GROUP BY t.id, ws.code, wd.code ORDER BY t.created_at DESC");
        List<Column> columns = List.of(Column.text("id", "Transfer #"), Column.status("status", "Status"),
                Column.text("source", "From"), Column.text("destination", "To"), Column.number("units", "Units"),
                Column.number("shortfall", "Lost in transit"), Column.datetime("created_at", "Created"),
                Column.datetime("dispatched_at", "Dispatched"), Column.datetime("received_at", "Received"));
        Rows rows = q.fetch();
        return rows.result(columns, count(rows, "transfer", "transfers") + ".");
    }

    private QueryResult reorderSuggestions(ValidatedPlan plan) {
        Query q = new Query("""
                SELECT p.sku, p.name, w.code AS warehouse, sup.name AS supplier, f.available, f.incoming,
                       f.days_of_cover, f.suggested_qty, f.suggested_qty * p.unit_cost AS est_cost, f.explanation
                FROM stock_forecasts f
                JOIN products p ON p.id = f.product_id
                JOIN warehouses w ON w.id = f.warehouse_id
                LEFT JOIN suppliers sup ON sup.id = p.supplier_id
                WHERE f.warehouse_id IN (:warehouseIds) AND f.suggested_qty > 0 AND p.archived = FALSE
                  AND\s""" + StockForecast.NOT_COVERED_SQL, plan);
        q.productFilter("p.id").categoryFilter("p.category").supplierFilter("p.supplier_id");
        q.sql.append(" ORDER BY f.days_of_cover ASC NULLS LAST, f.suggested_qty DESC");
        List<Column> columns = List.of(Column.text("sku", "SKU"), Column.text("name", "Product"),
                Column.text("warehouse", "Warehouse"), Column.text("supplier", "Supplier"),
                Column.number("available", "Available"), Column.number("incoming", "On order"),
                Column.decimal("days_of_cover", "Days of cover"), Column.number("suggested_qty", "Suggested qty"),
                Column.money("est_cost", "Est. cost"), Column.text("explanation", "Why"));
        Rows rows = q.fetch();
        return rows.result(columns, count(rows, "item needs", "items need") + " reordering in " + plan.warehouseLabel() + ".");
    }

    private QueryResult stockValue(ValidatedPlan plan) {
        Query q = new Query("""
                SELECT w.code AS warehouse, p.category, COUNT(DISTINCT p.id) AS products, SUM(s.on_hand) AS units,
                       SUM(s.on_hand * p.unit_cost) AS value
                FROM stock_levels s
                JOIN products p ON p.id = s.product_id
                JOIN warehouses w ON w.id = s.warehouse_id
                WHERE s.warehouse_id IN (:warehouseIds) AND p.archived = FALSE
                """, plan);
        q.productFilter("p.id").categoryFilter("p.category").supplierFilter("p.supplier_id");
        q.sql.append(" GROUP BY w.code, p.category ORDER BY value DESC");
        List<Column> columns = List.of(Column.text("warehouse", "Warehouse"), Column.text("category", "Category"),
                Column.number("products", "Products"), Column.number("units", "Units"), Column.money("value", "Value"));
        Rows rows = q.fetch();
        BigDecimal total = rows.list.stream().map(r -> (BigDecimal) r.get("value"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return rows.result(columns, "Total stock value " + total.setScale(2, RoundingMode.HALF_UP).toPlainString()
                + (rows.truncated ? " across the groups shown." : "."));
    }

    // ------------------------------------------------------------------ helpers

    private static String count(Rows rows, String singular, String plural) {
        int n = rows.list.size();
        return (rows.truncated ? "more than " + n : String.valueOf(n)) + " " + (n == 1 && !rows.truncated ? singular : plural);
    }

    /** Builder for one query: constant SQL plus constant filter fragments and bind parameters. */
    private final class Query {
        private final StringBuilder sql;
        private final MapSqlParameterSource params = new MapSqlParameterSource();
        private final ValidatedPlan plan;

        Query(String base, ValidatedPlan plan) {
            this.sql = new StringBuilder(base.strip());
            this.plan = plan;
            params.addValue("warehouseIds", plan.warehouseIds());
        }

        Query productFilter(String column) {
            if (!plan.productIds().isEmpty()) {
                sql.append(" AND ").append(column).append(" IN (:productIds)");
                params.addValue("productIds", plan.productIds());
            }
            return this;
        }

        Query categoryFilter(String column) {
            if (plan.category() != null) {
                sql.append(" AND ").append(column).append(" = :category");
                params.addValue("category", plan.category());
            }
            return this;
        }

        Query supplierFilter(String column) {
            if (plan.supplierId() != null) {
                sql.append(" AND ").append(column).append(" = :supplierId");
                params.addValue("supplierId", plan.supplierId());
            }
            return this;
        }

        Query statusFilter(String column) {
            if (!plan.statuses().isEmpty()) {
                sql.append(" AND ").append(column).append(" IN (:statuses)");
                params.addValue("statuses", plan.statuses());
            }
            return this;
        }

        Query dateFilter(String column) {
            if (plan.from() != null) {
                sql.append(" AND ").append(column).append(" >= :fromTs AND ").append(column).append(" < :toTs");
                dateParams();
            }
            return this;
        }

        Query dateParams() {
            params.addValue("fromTs", Timestamp.from(plan.from().atStartOfDay(ZoneOffset.UTC).toInstant()));
            params.addValue("toTs", Timestamp.from(plan.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
            return this;
        }

        Rows fetch() {
            sql.append(" LIMIT :limitPlusOne");
            params.addValue("limitPlusOne", plan.limit() + 1);
            List<Map<String, Object>> list = jdbc.query(sql.toString(), params, (rs, i) -> toRow(rs));
            boolean truncated = list.size() > plan.limit();
            return new Rows(truncated ? new ArrayList<>(list.subList(0, plan.limit())) : list, truncated);
        }
    }

    private record Rows(List<Map<String, Object>> list, boolean truncated) {
        QueryResult result(List<Column> columns, String summary) {
            return new QueryResult(columns, list, truncated, summary);
        }
    }

    /** Converts JDBC types to JSON-friendly ones (Timestamp -> Instant, Date -> LocalDate). */
    private static Map<String, Object> toRow(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            Object value = rs.getObject(i);
            if (value instanceof Timestamp ts) {
                value = ts.toInstant();
            } else if (value instanceof Date d) {
                value = d.toLocalDate();
            }
            row.put(meta.getColumnLabel(i), value);
        }
        return row;
    }
}
