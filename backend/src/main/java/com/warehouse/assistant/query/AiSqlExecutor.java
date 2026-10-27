package com.warehouse.assistant.query;

import com.warehouse.assistant.plan.PlanValidationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Runs a SELECT written by the AI against the views in schema {@code ai} (see V2 migration), as the
 * read-only role {@code warehouse_ai_reader}, limited to the user's warehouses. The transaction is
 * always rolled back, which also discards the warehouse scope it wrote.
 */
@Component
public class AiSqlExecutor {

    static final String ROLE = "warehouse_ai_reader";
    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_SQL_LENGTH = 4000;
    private static final Pattern STARTS_WITH_SELECT = Pattern.compile("^(select|with)\\b", Pattern.CASE_INSENSITIVE);
    // set_config('role', ...) can switch back to the connection's own login role, and these
    // functions run SQL given as text; U& escapes could disguise either name.
    private static final Pattern FORBIDDEN = Pattern.compile(
            "set_config|_to_xml|ts_stat|dblink|lo_import|lo_export|pg_read_|u&", Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public AiSqlExecutor(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public boolean isAvailable() {
        Boolean ok = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = ?) AND pg_has_role(current_user, ?, 'MEMBER')",
                Boolean.class, ROLE, ROLE);
        return Boolean.TRUE.equals(ok);
    }

    public QueryResult run(String sql, Collection<Long> warehouseIds, int maxRows) {
        String query = check(sql);
        return tx.execute(status -> {
            status.setRollbackOnly();
            jdbc.execute("SET LOCAL statement_timeout = " + TIMEOUT_MS);
            jdbc.batchUpdate("INSERT INTO ai_scope (warehouse_id) VALUES (?)",
                    warehouseIds.stream().map(id -> new Object[]{id}).toList());
            jdbc.execute("SET LOCAL search_path = ai");
            jdbc.execute("SET LOCAL ROLE " + ROLE);
            // The newlines keep a trailing "--" comment from swallowing the wrapper.
            return jdbc.query("SELECT * FROM (\n" + query + "\n) ai_query LIMIT " + (maxRows + 1), rs -> {
                ResultSetMetaData meta = rs.getMetaData();
                List<Column> columns = new ArrayList<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    columns.add(new Column(meta.getColumnLabel(i), meta.getColumnLabel(i), type(meta.getColumnType(i))));
                }
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next() && rows.size() <= maxRows) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columns.size(); i++) {
                        Object value = rs.getObject(i);
                        row.put(columns.get(i - 1).key(), value instanceof java.sql.Timestamp t ? t.toInstant() : value);
                    }
                    rows.add(row);
                }
                boolean truncated = rows.size() > maxRows;
                List<Map<String, Object>> shown = truncated ? rows.subList(0, maxRows) : rows;
                String summary = (truncated ? "More than " + maxRows : String.valueOf(shown.size()))
                        + (shown.size() == 1 && !truncated ? " row." : " rows.");
                return new QueryResult(columns, new ArrayList<>(shown), truncated, summary);
            });
        });
    }

    private static String check(String sql) {
        String trimmed = sql == null ? "" : sql.strip().replaceAll(";+\\s*$", "");
        if (trimmed.isEmpty() || trimmed.length() > MAX_SQL_LENGTH || trimmed.contains(";")
                || !STARTS_WITH_SELECT.matcher(trimmed).find()) {
            throw new PlanValidationException("The AI did not produce a single SELECT query.");
        }
        if (FORBIDDEN.matcher(trimmed).find()) {
            throw new PlanValidationException("The AI's query uses a function that is not allowed.");
        }
        return trimmed;
    }

    private static String type(int sqlType) {
        return switch (sqlType) {
            case Types.INTEGER, Types.BIGINT, Types.SMALLINT -> "number";
            case Types.NUMERIC, Types.DECIMAL, Types.DOUBLE, Types.REAL, Types.FLOAT -> "decimal";
            case Types.DATE -> "date";
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "datetime";
            default -> "text";
        };
    }
}
