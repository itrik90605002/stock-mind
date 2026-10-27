package com.warehouse.dashboard;

import com.warehouse.dashboard.DashboardDtos.Dashboard;
import com.warehouse.dashboard.DashboardDtos.MoverRow;
import com.warehouse.dashboard.DashboardDtos.NavCounts;
import com.warehouse.dashboard.DashboardDtos.Movers;
import com.warehouse.dashboard.DashboardDtos.Stats;
import com.warehouse.dashboard.DashboardDtos.Task;
import com.warehouse.dashboard.DashboardDtos.TaskGroup;
import com.warehouse.dashboard.DashboardDtos.TaskKind;
import com.warehouse.dashboard.DashboardDtos.TrendPoint;
import com.warehouse.dashboard.DashboardDtos.ValuationRow;
import com.warehouse.forecast.domain.StockForecast;
import com.warehouse.forecast.service.ReorderService;
import com.warehouse.security.AuthUser;
import com.warehouse.security.WarehouseAccess;
import com.warehouse.stock.service.StockQueryService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private static final String STATS_SQL = """
            SELECT
              (SELECT COUNT(*) FROM products WHERE archived = FALSE) AS active_products,
              (SELECT COALESCE(SUM(s.on_hand), 0) FROM stock_levels s WHERE s.warehouse_id IN (:ids)) AS units,
              (SELECT COALESCE(SUM(s.on_hand * p.unit_cost), 0) FROM stock_levels s JOIN products p ON p.id = s.product_id
                 WHERE s.warehouse_id IN (:ids)) AS value,
              (SELECT COUNT(*) FROM stock_levels s JOIN products p ON p.id = s.product_id
                 WHERE s.warehouse_id IN (:ids) AND p.archived = FALSE AND s.on_hand - s.reserved <= s.reorder_point) AS low,
              (SELECT COUNT(*) FROM purchase_orders WHERE warehouse_id IN (:ids)
                 AND status IN ('DRAFT', 'ORDERED', 'PARTIALLY_RECEIVED')) AS open_pos,
              (SELECT COUNT(*) FROM transfers WHERE status = 'DISPATCHED'
                 AND (source_warehouse_id IN (:ids) OR destination_warehouse_id IN (:ids))) AS in_transit,
              (SELECT COUNT(*) FROM stock_movements WHERE warehouse_id IN (:ids) AND created_at >= :todayStart) AS today,
              (SELECT COUNT(*) FROM stock_forecasts f JOIN products p ON p.id = f.product_id
                 WHERE f.warehouse_id IN (:ids) AND f.suggested_qty > 0 AND p.archived = FALSE
                   AND\s""" + StockForecast.NOT_COVERED_SQL + """
                 ) AS reorders
            """;

    private static final String TREND_SQL = """
            SELECT CAST(created_at AT TIME ZONE 'UTC' AS DATE) AS day,
                   SUM(CASE WHEN quantity > 0 THEN quantity ELSE 0 END) AS units_in,
                   SUM(CASE WHEN quantity < 0 THEN -quantity ELSE 0 END) AS units_out
            FROM stock_movements
            WHERE warehouse_id IN (:ids) AND created_at >= :since AND type <> 'ADJUSTMENT'
            GROUP BY day ORDER BY day
            """;

    /**
     * Every open purchase order and transfer in scope, tagged with the step it is waiting for.
     * Transfers count only on the side that acts next: drafts at the source, dispatched ones at the destination.
     */
    private static final String TASKS_SQL = """
            WITH po AS (
                SELECT o.id, o.status, w.code, s.name AS supplier, o.expected_date, o.created_at, o.ordered_at,
                       COALESCE(SUM(l.quantity), 0) AS total,
                       COALESCE(SUM(l.quantity - l.received_quantity), 0) AS remaining
                FROM purchase_orders o
                JOIN warehouses w ON w.id = o.warehouse_id
                JOIN suppliers s ON s.id = o.supplier_id
                LEFT JOIN purchase_order_lines l ON l.purchase_order_id = o.id
                WHERE o.warehouse_id IN (:ids) AND o.status IN ('DRAFT', 'ORDERED', 'PARTIALLY_RECEIVED')
                GROUP BY o.id, w.code, s.name
            ), tr AS (
                SELECT t.id, t.status, src.code AS src, dst.code AS dst, t.created_at, t.dispatched_at,
                       t.source_warehouse_id IN (:ids) AS from_mine, t.destination_warehouse_id IN (:ids) AS to_mine,
                       COALESCE(SUM(l.quantity), 0) AS units
                FROM transfers t
                JOIN warehouses src ON src.id = t.source_warehouse_id
                JOIN warehouses dst ON dst.id = t.destination_warehouse_id
                LEFT JOIN transfer_lines l ON l.transfer_id = t.id
                WHERE t.status IN ('DRAFT', 'DISPATCHED')
                  AND (t.source_warehouse_id IN (:ids) OR t.destination_warehouse_id IN (:ids))
                GROUP BY t.id, src.code, dst.code
            )
            SELECT 'PLACE_ORDER', id, supplier, code, total, expected_date, created_at FROM po WHERE status = 'DRAFT'
            UNION ALL
            SELECT 'RECEIVE_DELIVERY', id, supplier, code, remaining, expected_date, ordered_at FROM po WHERE status <> 'DRAFT'
            UNION ALL
            SELECT 'DISPATCH_TRANSFER', id, src || ' → ' || dst, src, units, NULL, created_at FROM tr
              WHERE status = 'DRAFT' AND from_mine
            UNION ALL
            SELECT 'RECEIVE_TRANSFER', id, src || ' → ' || dst, dst, units, NULL, dispatched_at FROM tr
              WHERE status = 'DISPATCHED' AND to_mine
            """;

    private static final String REORDER_COUNT_SQL = """
            SELECT COUNT(*) FROM stock_forecasts f JOIN products p ON p.id = f.product_id
            WHERE f.warehouse_id IN (:ids) AND f.suggested_qty > 0 AND p.archived = FALSE
              AND\s""" + StockForecast.NOT_COVERED_SQL;

    private static final int TASKS_PER_GROUP = 5;

    private static final String VALUATION_SQL = """
            SELECT w.code, p.category, COUNT(DISTINCT p.id), COALESCE(SUM(s.on_hand), 0),
                   COALESCE(SUM(s.on_hand * p.unit_cost), 0)
            FROM stock_levels s
            JOIN products p ON p.id = s.product_id
            JOIN warehouses w ON w.id = s.warehouse_id
            WHERE s.warehouse_id IN (:ids) AND p.archived = FALSE
            GROUP BY w.code, p.category
            ORDER BY w.code, 5 DESC
            """;

    private static final String MOVERS_SQL = """
            WITH usage AS (
                SELECT product_id, SUM(-quantity) AS units FROM stock_movements
                WHERE type IN ('ISSUE', 'TRANSFER_OUT') AND warehouse_id IN (:ids) AND created_at >= :since
                GROUP BY product_id
            ), stock AS (
                SELECT product_id, SUM(on_hand) AS on_hand FROM stock_levels WHERE warehouse_id IN (:ids)
                GROUP BY product_id
            )
            SELECT p.id, p.sku, p.name, p.category, COALESCE(u.units, 0) AS units_out, st.on_hand,
                   st.on_hand * p.unit_cost AS value
            FROM stock st JOIN products p ON p.id = st.product_id LEFT JOIN usage u ON u.product_id = st.product_id
            WHERE p.archived = FALSE
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final WarehouseAccess access;
    private final StockQueryService stockQueries;
    private final ReorderService reorders;
    private final Clock clock;

    public DashboardService(NamedParameterJdbcTemplate jdbc, WarehouseAccess access, StockQueryService stockQueries,
                            ReorderService reorders, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.stockQueries = stockQueries;
        this.reorders = reorders;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(AuthUser user, Long warehouseId) {
        List<Long> scope = access.scope(user, warehouseId);
        if (scope.isEmpty()) {
            return new Dashboard(new Stats(0, 0, BigDecimal.ZERO, 0, 0, 0, 0, 0), List.of(), List.of(), List.of(),
                    List.of());
        }
        LocalDate today = LocalDate.now(clock);
        MapSqlParameterSource params = new MapSqlParameterSource("ids", scope)
                .addValue("todayStart", Timestamp.from(today.atStartOfDay(ZoneOffset.UTC).toInstant()));
        Stats stats = jdbc.queryForObject(STATS_SQL, params, (rs, i) -> new Stats(rs.getLong(1), rs.getLong(2),
                rs.getBigDecimal(3), rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8)));

        var suggestions = reorders.suggestions(user, warehouseId).suggestions();
        return new Dashboard(stats,
                suggestions.subList(0, Math.min(5, suggestions.size())),
                tasks(user, scope, today),
                stockQueries.movements(user, warehouseId, null, null, null, null, null, null, 0, 8).content(),
                trend(scope, today, 30));
    }

    /**
     * Open documents grouped by the step they wait for. Deliveries are ordered by due date (late first);
     * everything else by how long it has been waiting. Clerks cannot place orders, so they get no such group.
     */
    private List<TaskGroup> tasks(AuthUser user, List<Long> scope, LocalDate today) {
        Map<TaskKind, List<Task>> byKind = new EnumMap<>(TaskKind.class);
        jdbc.query(TASKS_SQL, new MapSqlParameterSource("ids", scope), rs -> {
            TaskKind kind = TaskKind.valueOf(rs.getString(1));
            LocalDate due = rs.getObject(6, LocalDate.class);
            Timestamp since = rs.getTimestamp(7);
            byKind.computeIfAbsent(kind, k -> new ArrayList<>()).add(new Task(kind, rs.getLong(2), rs.getString(3),
                    rs.getString(4), rs.getLong(5), due, since == null ? null : since.toInstant(),
                    kind == TaskKind.RECEIVE_DELIVERY && due != null && due.isBefore(today)));
        });
        Comparator<Task> oldestFirst = Comparator.comparing(Task::since, Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<Task> dueFirst = Comparator.comparing(Task::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(oldestFirst);

        List<TaskGroup> groups = new ArrayList<>();
        for (TaskKind kind : TaskKind.values()) {
            if (kind == TaskKind.PLACE_ORDER && !user.isManagerOrAdmin()) {
                continue;
            }
            List<Task> items = byKind.getOrDefault(kind, List.of());
            List<Task> top = items.stream().sorted(kind == TaskKind.RECEIVE_DELIVERY ? dueFirst : oldestFirst)
                    .limit(TASKS_PER_GROUP).toList();
            groups.add(new TaskGroup(kind, items.size(), items.stream().filter(Task::late).count(), top));
        }
        return groups;
    }

    @Transactional(readOnly = true)
    public NavCounts navCounts(AuthUser user) {
        List<Long> scope = access.scope(user, null);
        if (scope.isEmpty()) {
            return new NavCounts(0, 0, 0, 0);
        }
        Map<TaskKind, Long> counts = new EnumMap<>(TaskKind.class);
        long late = 0;
        for (TaskGroup g : tasks(user, scope, LocalDate.now(clock))) {
            counts.put(g.kind(), g.count());
            late += g.late();
        }
        long toReorder = user.isManagerOrAdmin()
                ? jdbc.queryForObject(REORDER_COUNT_SQL, new MapSqlParameterSource("ids", scope), Long.class) : 0;
        return new NavCounts(toReorder,
                counts.getOrDefault(TaskKind.PLACE_ORDER, 0L) + counts.getOrDefault(TaskKind.RECEIVE_DELIVERY, 0L),
                counts.getOrDefault(TaskKind.DISPATCH_TRANSFER, 0L) + counts.getOrDefault(TaskKind.RECEIVE_TRANSFER, 0L),
                late);
    }

    @Transactional(readOnly = true)
    public List<ValuationRow> valuation(AuthUser user, Long warehouseId) {
        List<Long> scope = access.scope(user, warehouseId);
        if (scope.isEmpty()) {
            return List.of();
        }
        return jdbc.query(VALUATION_SQL, new MapSqlParameterSource("ids", scope), (rs, i) -> new ValuationRow(
                rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getBigDecimal(5)));
    }

    @Transactional(readOnly = true)
    public Movers movers(AuthUser user, Long warehouseId, int days) {
        List<Long> scope = access.scope(user, warehouseId);
        if (scope.isEmpty()) {
            return new Movers(days, List.of(), List.of());
        }
        MapSqlParameterSource params = new MapSqlParameterSource("ids", scope)
                .addValue("since", Timestamp.from(LocalDate.now(clock).minusDays(days - 1L)
                        .atStartOfDay(ZoneOffset.UTC).toInstant()));
        List<MoverRow> fastest = jdbc.query(MOVERS_SQL + " ORDER BY units_out DESC, p.sku LIMIT 10", params, this::mover);
        List<MoverRow> slowest = jdbc.query(MOVERS_SQL + " ORDER BY units_out ASC, value DESC, p.sku LIMIT 10", params,
                this::mover);
        return new Movers(days, fastest, slowest);
    }

    private MoverRow mover(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new MoverRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5),
                rs.getLong(6), rs.getBigDecimal(7));
    }

    private List<TrendPoint> trend(List<Long> scope, LocalDate today, int days) {
        LocalDate first = today.minusDays(days - 1L);
        Map<LocalDate, TrendPoint> byDay = new HashMap<>();
        jdbc.query(TREND_SQL, new MapSqlParameterSource("ids", scope)
                .addValue("since", Timestamp.from(first.atStartOfDay(ZoneOffset.UTC).toInstant())), rs -> {
            LocalDate day = rs.getDate(1).toLocalDate();
            byDay.put(day, new TrendPoint(day, rs.getLong(2), rs.getLong(3)));
        });
        List<TrendPoint> points = new ArrayList<>(days);
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            points.add(byDay.getOrDefault(d, new TrendPoint(d, 0, 0)));
        }
        return points;
    }
}
