package com.warehouse.forecast.service;

import com.warehouse.common.exception.BusinessRuleException;
import com.warehouse.config.AppProperties;
import com.warehouse.forecast.dto.ForecastRunResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Recomputes usage statistics and reorder suggestions for every active product/warehouse pair.
 *
 * <ol>
 *   <li>Read inputs in one read-only snapshot: stock levels, daily outbound usage (ISSUE and
 *       TRANSFER_OUT over the long window, full UTC days only) and quantities on open orders.</li>
 *   <li>Apply {@link ReorderMath} to each pair.</li>
 *   <li>Explain suggestions: templates for all, AI re-phrasing for the most urgent (outside any
 *       transaction, so a slow model never holds a database connection).</li>
 *   <li>Replace the {@code stock_forecasts} table contents atomically.</li>
 * </ol>
 *
 * Runs from the daily job or on demand; concurrent runs are rejected rather than queued.
 */
@Service
public class ForecastService {

    private static final Logger log = LoggerFactory.getLogger(ForecastService.class);

    private static final String LEVELS_SQL = """
            SELECT s.product_id, s.warehouse_id, s.on_hand - s.reserved AS available, s.reorder_point,
                   COALESCE(s.lead_time_days, sup.lead_time_days, :defaultLead) AS lead_time,
                   p.sku, p.name, p.unit, w.code
            FROM stock_levels s
            JOIN products p ON p.id = s.product_id
            JOIN warehouses w ON w.id = s.warehouse_id
            LEFT JOIN suppliers sup ON sup.id = p.supplier_id
            WHERE p.archived = FALSE AND w.active = TRUE
            """;

    private static final String USAGE_SQL = """
            SELECT product_id, warehouse_id, CAST(created_at AT TIME ZONE 'UTC' AS DATE) AS day, -SUM(quantity) AS units
            FROM stock_movements
            WHERE type IN ('ISSUE', 'TRANSFER_OUT') AND created_at >= :since AND created_at < :until
            GROUP BY product_id, warehouse_id, day
            """;

    private static final String INCOMING_SQL = """
            SELECT l.product_id, o.warehouse_id, SUM(l.quantity - l.received_quantity) AS units
            FROM purchase_order_lines l JOIN purchase_orders o ON o.id = l.purchase_order_id
            WHERE o.status IN ('ORDERED', 'PARTIALLY_RECEIVED')
            GROUP BY l.product_id, o.warehouse_id
            """;

    private static final String INSERT_SQL = """
            INSERT INTO stock_forecasts (product_id, warehouse_id, avg_daily_usage, usage_stddev, trend_pct,
                lead_time_days, safety_stock, available, incoming, days_of_cover, suggested_qty, explanation,
                explanation_by_ai, computed_at)
            VALUES (:productId, :warehouseId, :avg, :stddev, :trend, :lead, :safety, :available, :incoming, :cover,
                :suggested, :explanation, :byAi, :computedAt)
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;
    private final ExplanationWriter explanationWriter;
    private final AppProperties.Forecast settings;
    private final int maxAiExplanations;
    private final Clock clock;
    private final ReentrantLock runLock = new ReentrantLock();

    public ForecastService(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                           ExplanationWriter explanationWriter, AppProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
        this.readTx.setIsolationLevel(TransactionTemplate.ISOLATION_REPEATABLE_READ);
        this.writeTx = new TransactionTemplate(transactionManager);
        this.explanationWriter = explanationWriter;
        this.settings = properties.forecast();
        this.maxAiExplanations = properties.ai().maxExplanations();
        this.clock = clock;
    }

    private record Pair(long productId, long warehouseId) {
    }

    private record Level(Pair pair, int available, int reorderPoint, int leadTime, String sku, String name,
                         String unit, String warehouseCode) {
    }

    private record Computed(Level level, int incoming, ReorderMath.Result result) {
    }

    private record Inputs(List<Level> levels, Map<Pair, int[]> usage, Map<Pair, Integer> incoming) {
    }

    public ForecastRunResult recompute() {
        if (!runLock.tryLock()) {
            throw new BusinessRuleException("FORECAST_RUNNING", "A forecast run is already in progress");
        }
        try {
            long started = System.currentTimeMillis();
            Instant now = clock.instant();
            Inputs inputs = readTx.execute(status -> loadInputs(now));

            List<Computed> computed = new ArrayList<>(inputs.levels().size());
            for (Level level : inputs.levels()) {
                int incoming = inputs.incoming().getOrDefault(level.pair(), 0);
                int[] usage = inputs.usage().getOrDefault(level.pair(), new int[settings.longWindowDays()]);
                computed.add(new Computed(level, incoming, ReorderMath.compute(new ReorderMath.Inputs(
                        usage, level.available(), incoming, level.reorderPoint(), level.leadTime()), settings)));
            }

            Map<Integer, String> explanations = new HashMap<>();
            Map<Integer, String> aiTexts = explain(computed, explanations);

            writeTx.executeWithoutResult(status -> {
                jdbc.getJdbcTemplate().update("DELETE FROM stock_forecasts");
                MapSqlParameterSource[] batch = new MapSqlParameterSource[computed.size()];
                for (int i = 0; i < computed.size(); i++) {
                    batch[i] = row(computed.get(i), explanations.get(i), aiTexts.containsKey(i), now);
                }
                jdbc.batchUpdate(INSERT_SQL, batch);
            });

            int suggestions = (int) computed.stream().filter(c -> c.result().suggestedQty() > 0).count();
            long duration = System.currentTimeMillis() - started;
            log.info("Forecast run: {} pairs, {} suggestions, {} AI explanations in {} ms", computed.size(),
                    suggestions, aiTexts.size(), duration);
            return new ForecastRunResult(now, computed.size(), suggestions, aiTexts.size(), duration);
        } finally {
            runLock.unlock();
        }
    }

    private Inputs loadInputs(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        LocalDate firstDay = today.minusDays(settings.longWindowDays());

        List<Level> levels = jdbc.query(LEVELS_SQL, new MapSqlParameterSource("defaultLead", settings.defaultLeadTimeDays()),
                (rs, i) -> new Level(new Pair(rs.getLong(1), rs.getLong(2)), rs.getInt(3), rs.getInt(4), rs.getInt(5),
                        rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9)));

        Map<Pair, int[]> usage = new HashMap<>();
        MapSqlParameterSource window = new MapSqlParameterSource()
                .addValue("since", Timestamp.from(firstDay.atStartOfDay(ZoneOffset.UTC).toInstant()))
                .addValue("until", Timestamp.from(today.atStartOfDay(ZoneOffset.UTC).toInstant()));
        jdbc.query(USAGE_SQL, window, rs -> {
            Pair pair = new Pair(rs.getLong(1), rs.getLong(2));
            int index = (int) ChronoUnit.DAYS.between(firstDay, rs.getDate(3).toLocalDate());
            if (index >= 0 && index < settings.longWindowDays()) {
                usage.computeIfAbsent(pair, p -> new int[settings.longWindowDays()])[index] = rs.getInt(4);
            }
        });

        Map<Pair, Integer> incoming = new HashMap<>();
        jdbc.query(INCOMING_SQL, rs -> {
            incoming.put(new Pair(rs.getLong(1), rs.getLong(2)), rs.getInt(3));
        });
        return new Inputs(levels, usage, incoming);
    }

    /** Fills {@code explanations} (index -> text) and returns the subset written by AI. */
    private Map<Integer, String> explain(List<Computed> computed, Map<Integer, String> explanations) {
        List<ExplanationWriter.Facts> facts = new ArrayList<>();
        for (int i = 0; i < computed.size(); i++) {
            Computed c = computed.get(i);
            if (c.result().suggestedQty() > 0) {
                ExplanationWriter.Facts f = new ExplanationWriter.Facts(i, c.level().sku(), c.level().name(),
                        c.level().warehouseCode(), c.level().unit(), c.result().avgDailyUsage(), c.result().trendPct(),
                        c.result().daysOfCover(), c.level().leadTime(), c.level().available(), c.incoming(),
                        c.result().safetyStock(), c.result().suggestedQty());
                facts.add(f);
                explanations.put(i, ExplanationWriter.template(f, settings.reviewPeriodDays()));
            }
        }
        List<ExplanationWriter.Facts> mostUrgent = facts.stream()
                .sorted(Comparator.comparing((ExplanationWriter.Facts f) ->
                        f.daysOfCover() == null ? Double.MAX_VALUE : f.daysOfCover()))
                .limit(maxAiExplanations)
                .toList();
        Map<Integer, String> aiTexts = explanationWriter.aiExplanations(mostUrgent);
        explanations.putAll(aiTexts);
        return aiTexts;
    }

    private static MapSqlParameterSource row(Computed c, String explanation, boolean byAi, Instant now) {
        ReorderMath.Result r = c.result();
        return new MapSqlParameterSource()
                .addValue("productId", c.level().pair().productId())
                .addValue("warehouseId", c.level().pair().warehouseId())
                .addValue("avg", scale(r.avgDailyUsage(), 3))
                .addValue("stddev", scale(r.stddev(), 3))
                .addValue("trend", r.trendPct() == null ? null : scale(Math.max(-9999, Math.min(9999, r.trendPct())), 1))
                .addValue("lead", c.level().leadTime())
                .addValue("safety", r.safetyStock())
                .addValue("available", c.level().available())
                .addValue("incoming", c.incoming())
                .addValue("cover", r.daysOfCover() == null ? null : scale(Math.min(r.daysOfCover(), 99_999), 1))
                .addValue("suggested", r.suggestedQty())
                .addValue("explanation", explanation)
                .addValue("byAi", byAi)
                .addValue("computedAt", Timestamp.from(now));
    }

    private static BigDecimal scale(double value, int digits) {
        return BigDecimal.valueOf(value).setScale(digits, RoundingMode.HALF_UP);
    }
}
