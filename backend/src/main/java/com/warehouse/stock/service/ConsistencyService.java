package com.warehouse.stock.service;

import com.warehouse.security.AuthUser;
import com.warehouse.security.WarehouseAccess;
import com.warehouse.stock.dto.ConsistencyReport;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Proves the stock tables are derivable from the ledger: for every product/warehouse pair,
 * {@code on_hand} must equal the sum of its ledger entries and {@code reserved} must equal the
 * quantity on draft transfers leaving that warehouse.
 *
 * <p>Runs in a REPEATABLE READ snapshot so concurrent movements cannot produce false alarms.
 */
@Service
public class ConsistencyService {

    private static final String MISMATCH_SQL = """
            WITH scoped_levels AS (
                SELECT product_id, warehouse_id, on_hand, reserved FROM stock_levels WHERE warehouse_id IN (:ids)
            ), ledger AS (
                SELECT product_id, warehouse_id, SUM(quantity) AS qty
                FROM stock_movements WHERE warehouse_id IN (:ids) GROUP BY product_id, warehouse_id
            ), reservations AS (
                SELECT tl.product_id, t.source_warehouse_id AS warehouse_id, SUM(tl.quantity) AS qty
                FROM transfer_lines tl JOIN transfers t ON t.id = tl.transfer_id
                WHERE t.status = 'DRAFT' AND t.source_warehouse_id IN (:ids)
                GROUP BY tl.product_id, t.source_warehouse_id
            ), compared AS (
                SELECT COALESCE(s.product_id, l.product_id) AS product_id,
                       COALESCE(s.warehouse_id, l.warehouse_id) AS warehouse_id,
                       COALESCE(s.on_hand, 0) AS recorded_on_hand, COALESCE(l.qty, 0) AS ledger_on_hand,
                       COALESCE(s.reserved, 0) AS recorded_reserved
                FROM scoped_levels s
                FULL OUTER JOIN ledger l ON l.product_id = s.product_id AND l.warehouse_id = s.warehouse_id
            )
            SELECT c.product_id, p.sku, c.warehouse_id, w.code, c.recorded_on_hand, c.ledger_on_hand,
                   c.recorded_reserved, COALESCE(r.qty, 0) AS expected_reserved
            FROM compared c
            JOIN products p ON p.id = c.product_id
            JOIN warehouses w ON w.id = c.warehouse_id
            LEFT JOIN reservations r ON r.product_id = c.product_id AND r.warehouse_id = c.warehouse_id
            WHERE c.recorded_on_hand <> c.ledger_on_hand OR c.recorded_reserved <> COALESCE(r.qty, 0)
            ORDER BY p.sku, w.code
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final WarehouseAccess access;
    private final Clock clock;

    public ConsistencyService(NamedParameterJdbcTemplate jdbc, WarehouseAccess access, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ConsistencyReport check(AuthUser user, Long warehouseId) {
        List<Long> scope = access.scope(user, warehouseId);
        if (scope.isEmpty()) {
            return new ConsistencyReport(clock.instant(), 0, List.of());
        }
        MapSqlParameterSource params = new MapSqlParameterSource("ids", scope);
        Integer checked = jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_levels WHERE warehouse_id IN (:ids)", params, Integer.class);
        List<ConsistencyReport.Mismatch> mismatches = jdbc.query(MISMATCH_SQL, params, (rs, i) ->
                new ConsistencyReport.Mismatch(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4),
                        rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getInt(8)));
        return new ConsistencyReport(clock.instant(), checked == null ? 0 : checked, mismatches);
    }
}
