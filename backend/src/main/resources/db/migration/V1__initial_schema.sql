-- =============================================================================
-- Warehouse Inventory System - initial schema (PostgreSQL).
--
-- Consistency rules are enforced by the database as well as the application:
--   * stock_levels: on_hand >= 0, 0 <= reserved <= on_hand, optimistic-lock version column
--   * stock_movements: append-only (UPDATE/DELETE rejected by trigger)
--   * idempotency_records: one stored result per (user, key)
-- =============================================================================

-- ---------------------------------------------------------------- accounts
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'MANAGER', 'CLERK')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    status_reason VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL
);

-- ---------------------------------------------------------------- warehouses
CREATE TABLE warehouses (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(20)  NOT NULL UNIQUE,
    name       VARCHAR(120) NOT NULL,
    address    VARCHAR(255),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL
);

-- Which warehouses a Manager or Clerk may act on (Admins see all).
CREATE TABLE user_warehouses (
    user_id      BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses (id),
    PRIMARY KEY (user_id, warehouse_id)
);

-- ---------------------------------------------------------------- suppliers
CREATE TABLE suppliers (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(120) NOT NULL UNIQUE,
    contact_email  VARCHAR(255),
    phone          VARCHAR(40),
    lead_time_days INT          NOT NULL CHECK (lead_time_days BETWEEN 0 AND 365),
    created_at     TIMESTAMPTZ  NOT NULL
);

-- ---------------------------------------------------------------- products
CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    sku         VARCHAR(40)    NOT NULL UNIQUE,
    name        VARCHAR(200)   NOT NULL,
    category    VARCHAR(80)    NOT NULL,
    unit        VARCHAR(20)    NOT NULL,
    unit_cost   NUMERIC(12, 2) NOT NULL CHECK (unit_cost >= 0),
    description TEXT,
    supplier_id BIGINT REFERENCES suppliers (id),
    archived    BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ    NOT NULL,
    updated_at  TIMESTAMPTZ    NOT NULL
);
CREATE INDEX idx_products_category ON products (category);
CREATE INDEX idx_products_name_lower ON products (LOWER(name));

-- ---------------------------------------------------------------- stock
-- One row per product per warehouse. "available" = on_hand - reserved.
CREATE TABLE stock_levels (
    id             BIGSERIAL PRIMARY KEY,
    product_id     BIGINT      NOT NULL REFERENCES products (id),
    warehouse_id   BIGINT      NOT NULL REFERENCES warehouses (id),
    on_hand        INT         NOT NULL DEFAULT 0,
    reserved       INT         NOT NULL DEFAULT 0,
    reorder_point  INT         NOT NULL DEFAULT 0 CHECK (reorder_point >= 0),
    -- Overrides the supplier's lead time for this warehouse when set.
    lead_time_days INT CHECK (lead_time_days BETWEEN 0 AND 365),
    version        BIGINT      NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_stock_product_warehouse UNIQUE (product_id, warehouse_id),
    CONSTRAINT ck_stock_on_hand_non_negative CHECK (on_hand >= 0),
    CONSTRAINT ck_stock_reserved_range CHECK (reserved >= 0 AND reserved <= on_hand)
);
CREATE INDEX idx_stock_warehouse ON stock_levels (warehouse_id);

-- The ledger: every change to on_hand is one row. quantity is signed (+ in, - out).
CREATE TABLE stock_movements (
    id             BIGSERIAL PRIMARY KEY,
    product_id     BIGINT      NOT NULL REFERENCES products (id),
    warehouse_id   BIGINT      NOT NULL REFERENCES warehouses (id),
    type           VARCHAR(20) NOT NULL CHECK (type IN ('RECEIPT', 'ISSUE', 'ADJUSTMENT', 'TRANSFER_OUT', 'TRANSFER_IN')),
    quantity       INT         NOT NULL CHECK (quantity <> 0),
    balance_after  INT         NOT NULL CHECK (balance_after >= 0),
    reason         VARCHAR(255),
    reference_type VARCHAR(20)  NOT NULL CHECK (reference_type IN ('PURCHASE_ORDER', 'TRANSFER', 'MANUAL', 'OPENING')),
    reference_id   BIGINT,
    -- Set when this entry reverses an earlier one.
    reverses_id    BIGINT REFERENCES stock_movements (id),
    user_id        BIGINT REFERENCES users (id),
    created_at     TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_movements_product_wh_time ON stock_movements (product_id, warehouse_id, created_at);
CREATE INDEX idx_movements_time ON stock_movements (created_at DESC);
CREATE INDEX idx_movements_reference ON stock_movements (reference_type, reference_id);
CREATE UNIQUE INDEX uq_movements_reverses ON stock_movements (reverses_id) WHERE reverses_id IS NOT NULL;

CREATE FUNCTION reject_ledger_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'stock_movements is append-only; record a reversing entry instead';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_stock_movements_append_only
    BEFORE UPDATE OR DELETE ON stock_movements
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_change();

-- ---------------------------------------------------------------- transfers
CREATE TABLE transfers (
    id                       BIGSERIAL PRIMARY KEY,
    source_warehouse_id      BIGINT      NOT NULL REFERENCES warehouses (id),
    destination_warehouse_id BIGINT      NOT NULL REFERENCES warehouses (id),
    status                   VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'DISPATCHED', 'RECEIVED', 'CANCELLED')),
    note                     VARCHAR(500),
    created_by               BIGINT      NOT NULL REFERENCES users (id),
    created_at               TIMESTAMPTZ NOT NULL,
    dispatched_by            BIGINT REFERENCES users (id),
    dispatched_at            TIMESTAMPTZ,
    received_by              BIGINT REFERENCES users (id),
    received_at              TIMESTAMPTZ,
    cancelled_at             TIMESTAMPTZ,
    version                  BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_transfer_distinct_warehouses CHECK (source_warehouse_id <> destination_warehouse_id)
);
CREATE INDEX idx_transfers_status ON transfers (status);

CREATE TABLE transfer_lines (
    id                BIGSERIAL PRIMARY KEY,
    transfer_id       BIGINT NOT NULL REFERENCES transfers (id) ON DELETE CASCADE,
    product_id        BIGINT NOT NULL REFERENCES products (id),
    quantity          INT    NOT NULL CHECK (quantity > 0),
    received_quantity INT CHECK (received_quantity >= 0 AND received_quantity <= quantity),
    CONSTRAINT uq_transfer_line_product UNIQUE (transfer_id, product_id)
);

-- ---------------------------------------------------------------- purchase orders
CREATE TABLE purchase_orders (
    id            BIGSERIAL PRIMARY KEY,
    supplier_id   BIGINT      NOT NULL REFERENCES suppliers (id),
    warehouse_id  BIGINT      NOT NULL REFERENCES warehouses (id),
    status        VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'ORDERED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED')),
    expected_date DATE,
    note          VARCHAR(500),
    created_by    BIGINT      NOT NULL REFERENCES users (id),
    created_at    TIMESTAMPTZ NOT NULL,
    ordered_at    TIMESTAMPTZ,
    closed_at     TIMESTAMPTZ,
    version       BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_po_status ON purchase_orders (status);

CREATE TABLE purchase_order_lines (
    id                BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT         NOT NULL REFERENCES purchase_orders (id) ON DELETE CASCADE,
    product_id        BIGINT         NOT NULL REFERENCES products (id),
    quantity          INT            NOT NULL CHECK (quantity > 0),
    received_quantity INT            NOT NULL DEFAULT 0,
    unit_cost         NUMERIC(12, 2) NOT NULL CHECK (unit_cost >= 0),
    CONSTRAINT uq_po_line_product UNIQUE (purchase_order_id, product_id),
    CONSTRAINT ck_po_line_received CHECK (received_quantity >= 0 AND received_quantity <= quantity)
);

-- ---------------------------------------------------------------- forecasts & reorder suggestions
-- Recomputed by the daily job (or on demand) from the ledger.
CREATE TABLE stock_forecasts (
    id               BIGSERIAL PRIMARY KEY,
    product_id       BIGINT         NOT NULL REFERENCES products (id),
    warehouse_id     BIGINT         NOT NULL REFERENCES warehouses (id),
    avg_daily_usage  NUMERIC(12, 3) NOT NULL,
    usage_stddev     NUMERIC(12, 3) NOT NULL,
    trend_pct        NUMERIC(8, 1),
    lead_time_days   INT            NOT NULL,
    safety_stock     INT            NOT NULL,
    available        INT            NOT NULL,
    incoming         INT            NOT NULL,
    days_of_cover    NUMERIC(10, 1),
    suggested_qty    INT            NOT NULL,
    explanation      VARCHAR(500),
    explanation_by_ai BOOLEAN       NOT NULL DEFAULT FALSE,
    computed_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_forecast_product_warehouse UNIQUE (product_id, warehouse_id)
);

-- ---------------------------------------------------------------- AI assistant log
CREATE TABLE ai_query_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    question    VARCHAR(500) NOT NULL,
    plan_json   TEXT,
    outcome     VARCHAR(20)  NOT NULL CHECK (outcome IN ('ANSWERED', 'REFUSED', 'INVALID_PLAN', 'AI_UNAVAILABLE', 'FAILED')),
    message     VARCHAR(500),
    row_count   INT,
    duration_ms INT          NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_ai_logs_time ON ai_query_logs (created_at DESC);

-- ---------------------------------------------------------------- idempotency
-- Stores the response of a stock-changing request so a retried request (same Idempotency-Key)
-- returns the original result instead of recording the movement twice.
CREATE TABLE idempotency_records (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id),
    idem_key      VARCHAR(100) NOT NULL,
    operation     VARCHAR(60)  NOT NULL,
    response_json TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_idempotency_user_key UNIQUE (user_id, idem_key)
);
CREATE INDEX idx_idempotency_created ON idempotency_records (created_at);
