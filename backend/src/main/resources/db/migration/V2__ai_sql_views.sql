ALTER TABLE stock_forecasts DROP COLUMN explanation_by_ai;

-- AI-written SQL only ever sees the views in schema "ai", run as role warehouse_ai_reader.
-- Each request writes the user's warehouses into ai_scope inside its own transaction and rolls
-- it back afterwards; the views show only those warehouses, and the AI role cannot touch ai_scope.
CREATE TABLE ai_scope (warehouse_id BIGINT NOT NULL);

CREATE SCHEMA ai;

CREATE VIEW ai.warehouses AS
SELECT id, code, name, active FROM warehouses
WHERE id IN (SELECT warehouse_id FROM ai_scope);

CREATE VIEW ai.suppliers AS
SELECT id, name, lead_time_days FROM suppliers;

CREATE VIEW ai.products AS
SELECT id, sku, name, category, unit, unit_cost, supplier_id, archived FROM products;

CREATE VIEW ai.stock AS
SELECT product_id, warehouse_id, on_hand, reserved, on_hand - reserved AS available, reorder_point, lead_time_days
FROM stock_levels
WHERE warehouse_id IN (SELECT warehouse_id FROM ai_scope);

CREATE VIEW ai.movements AS
SELECT id, product_id, warehouse_id, type, quantity, balance_after, reason, reference_type, reference_id, created_at
FROM stock_movements
WHERE warehouse_id IN (SELECT warehouse_id FROM ai_scope);

CREATE VIEW ai.purchase_orders AS
SELECT id, supplier_id, warehouse_id, status, expected_date, created_at, ordered_at, closed_at
FROM purchase_orders
WHERE warehouse_id IN (SELECT warehouse_id FROM ai_scope);

CREATE VIEW ai.purchase_order_lines AS
SELECT l.purchase_order_id, l.product_id, l.quantity, l.received_quantity, l.unit_cost
FROM purchase_order_lines l JOIN purchase_orders o ON o.id = l.purchase_order_id
WHERE o.warehouse_id IN (SELECT warehouse_id FROM ai_scope);

CREATE VIEW ai.transfers AS
SELECT id, source_warehouse_id, destination_warehouse_id, status, created_at, dispatched_at, received_at, cancelled_at
FROM transfers
WHERE source_warehouse_id IN (SELECT warehouse_id FROM ai_scope)
   OR destination_warehouse_id IN (SELECT warehouse_id FROM ai_scope);

CREATE VIEW ai.transfer_lines AS
SELECT l.transfer_id, l.product_id, l.quantity, l.received_quantity
FROM transfer_lines l JOIN transfers t ON t.id = l.transfer_id
WHERE t.source_warehouse_id IN (SELECT warehouse_id FROM ai_scope)
   OR t.destination_warehouse_id IN (SELECT warehouse_id FROM ai_scope);

CREATE VIEW ai.reorder_suggestions AS
SELECT product_id, warehouse_id, avg_daily_usage, days_of_cover, available, incoming, lead_time_days,
       safety_stock, suggested_qty, computed_at
FROM stock_forecasts
WHERE warehouse_id IN (SELECT warehouse_id FROM ai_scope);

-- Some hosted databases don't allow creating roles. The app then reports AI-written SQL as
-- unavailable and keeps answering through the prewritten queries.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'warehouse_ai_reader') THEN
        CREATE ROLE warehouse_ai_reader NOLOGIN;
    END IF;
    EXECUTE format('GRANT warehouse_ai_reader TO %I', current_user);
    GRANT USAGE ON SCHEMA ai TO warehouse_ai_reader;
    GRANT SELECT ON ALL TABLES IN SCHEMA ai TO warehouse_ai_reader;
EXCEPTION WHEN insufficient_privilege THEN
    RAISE NOTICE 'Could not set up warehouse_ai_reader; AI-written SQL will be unavailable';
END $$;
