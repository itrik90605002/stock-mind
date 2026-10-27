# StockPilot: AI Warehouse Inventory System

Tracks stock across warehouses, transfers between them, purchase orders and every stock movement.
You can ask questions in plain English ("Which items will run out next week?"), and it suggests
reorder quantities based on past usage.

**Stack:** Spring Boot 3.3 (Java 21) · PostgreSQL + Flyway · React 18 + TypeScript + Vite · any
OpenAI-compatible chat model (Gemini free tier when deployed, Ollama locally).

The full feature list is in [project-overview.md](project-overview.md).

---

## How the app is organised

The navigation follows the path stock takes: **spot → replenish → receive → track**.

| Section | What it is for |
|---|---|
| **Home** | Your to-do list for your role: items to reorder, draft orders to place, deliveries to receive (late ones first), transfers to dispatch or receive. Each item opens the document where you do the next step. |
| **Inventory** | Stock levels per warehouse (issue, adjust, reorder settings) and the product catalogue. A product page shows its stock everywhere, its actions and its history. |
| **Replenish** | Step 1 *What to reorder* (suggestions from usage) → step 2 *Order & receive* (draft → placed → received), with Suppliers alongside. |
| **Transfers** | Move stock between warehouses: draft (reserves stock) → dispatch → receive. |
| **Movements** | The append-only ledger and the consistency check. |
| **Reports** | Valuation and fast/slow movers (Managers and Admins). |

**Ask about your stock** sits at the top of the sidebar and on Home. Purchase-order and transfer
pages show a lifecycle stepper with the next step spelled out. The old URLs (`/stock`, `/products`,
`/reorder`, `/purchase-orders`, `/suppliers`, `/ledger`) redirect to their new places.

---

## Highlights

### 1. Stock stays consistent during concurrent updates
- **Optimistic locking.** Every `stock_levels` row has a `@Version` column. If two people change
  the same stock at the same moment, the second commit fails. `StockCommandExecutor` then rolls the
  whole command back and re-runs it on fresh data (up to 4 attempts, with a randomised back-off).
  Business rules are checked again on each attempt, so a retry can still fail with "insufficient stock".
- **The database refuses bad states.** CHECK constraints require `on_hand >= 0` and
  `0 <= reserved <= on_hand`.
- **The stock change and its ledger entry commit together.** All stock changes go through
  `StockLedgerService`, which must run inside an existing transaction
  (`Propagation.MANDATORY`).
- **Idempotency keys.** Stock-changing POSTs accept an `Idempotency-Key` header. The response is
  stored in the same transaction as the change, so a double click or a network retry returns the
  original result instead of recording the movement twice. Two simultaneous requests with the same
  key collide on a unique index, and the loser returns the winner's result.
- **Documents can't be applied twice.** Transfers and purchase orders also have a version column,
  so two users can't dispatch or receive the same document twice.

Verified against the running app:
- 30 parallel "issue 1 unit" requests against an item with 9 available gave exactly 9 successes,
  0 left in stock and no negative stock.
- 10 parallel identical requests with one Idempotency-Key produced exactly one movement.
- 2 parallel dispatches of the same transfer gave one success and one 409.

### 2. Audit ledger of every movement
- `stock_movements` is **append-only**. Hibernate maps it as `@Immutable`, and a Postgres trigger
  rejects any `UPDATE` or `DELETE`.
- Each entry records the signed quantity, the balance after the movement, the reason, the user and
  the source document (purchase order, transfer, manual or opening balance).
- Mistakes are corrected with a **reversing entry** that points at the original. Each entry can be
  reversed once.
- **Verify ledger** (Movements page) rebuilds every stock level from the ledger and from open
  transfer reservations in a REPEATABLE READ snapshot, and reports any mismatch.

### 3. Safe natural-language-to-query handling
```
question ──model──▶ QueryPlan (untrusted JSON)
         ──PlanValidator──▶ ValidatedPlan (allow-listed, resolved to ids, permission-scoped)
         ──AssistantQueryExecutor──▶ rows from a prewritten, read-only query
```
- The model **never writes SQL** and never sees the schema or the data. It only picks one of 9 fixed
  question types (stockout forecast, low stock, movement history, ...) and fills in filters.
- The validator parses every enum strictly. Product, warehouse, supplier and category text is used
  only as a lookup key through parameterised queries and resolved to ids. Dates and row limits are
  bounded.
- The warehouse filter is **always narrowed to the user's own warehouses**, so a clerk asking about
  another warehouse is refused.
- Queries run in a **read-only transaction with a 5-second statement timeout**. All values are
  bind parameters.
- Summaries are computed from the result rows, not generated by the model, so an answer can't
  contain invented numbers. The UI shows how the question was interpreted, so the user can check it.
- Every question is logged with its plan and outcome (Admin → AI query log). Questions are
  rate-limited per user.
- If the AI is down, a circuit breaker fails fast. Quick-question buttons (fixed plans, no AI
  needed) and all other pages keep working.

### 4. Reorder suggestions from past usage
Quantities come from a formula in `ReorderMath`, not from the AI:

```
avg/day       = 0.6 × mean(last 30 days) + 0.4 × mean(last 90 days)     (ISSUE + TRANSFER_OUT)
safety stock  = 1.65 × σ(daily usage) × √lead time                      (~95% service level)
trigger       = max(reorder point, avg × lead time + safety stock)
order-up-to   = max(avg × (lead time + 7-day review) + safety stock, reorder point)
suggestion    = order-up-to − (available + on open POs), when available + on order ≤ trigger
```
The AI only re-phrases the one-line "why" for the most urgent suggestions, using just the computed
numbers. A template explanation is used when AI is unavailable. Managers can select suggestions and
create draft purchase orders, one per supplier and warehouse.
Suggestions are a snapshot from the daily forecast run, so an item is hidden from the list (and from
the counts and the assistant) as soon as a purchase order the snapshot did not see covers it: a draft,
or any order raised after the run. That stops the same item being ordered twice before the next run.

---

## Project structure

```
backend/   Spring Boot API, organised by feature: account, warehouse, supplier, product, stock,
           transfer, purchasing, forecast, assistant, dashboard, jobs, seed
           (each with domain / service / dto / web layers)
  src/main/resources/db/migration/   Flyway schema, including constraints and the ledger trigger
  src/main/resources/seed/*.json     Demo master data: 120 products, 10 suppliers, 4 warehouses, 7 users
frontend/  React + TypeScript SPA (no UI library; plain CSS with light and dark themes)
render.yaml  Render blueprint for the backend
```

---

## Running locally

**Prerequisites:** Java 21, Maven, Node 20+, PostgreSQL 14+.

```bash
# 1. Database
createdb warehouse            # defaults: user postgres / password postgres on localhost:5432

# 2. Backend (http://localhost:8080, Swagger UI at /swagger-ui.html)
cd backend
mvn spring-boot:run
# First start loads the demo data (~4 s): 4 warehouses, 120 products, ~46k ledger entries.

# 3. Frontend (http://localhost:5173)
cd frontend
cp .env.example .env          # VITE_API_BASE_URL=http://localhost:8080
npm install
npm run dev
```

**AI (optional).** Without AI, the assistant reports "unavailable", quick questions still work, and
reorder explanations use templates. To turn AI on, choose one of these:
- Ollama: `ollama pull llama3.1`. The defaults point at `http://localhost:11434/v1`.
- Gemini: set `AI_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai`,
  `AI_API_KEY=<key>` and `AI_CHAT_MODEL=gemini-2.5-flash`.

### Demo accounts

| Role | Email | Password | Warehouses |
|---|---|---|---|
| Admin | admin@warehouse.local | Admin@12345 | all |
| Manager | manager@warehouse.local | Manager@123 | PUN-01, MUM-02 |
| Manager | north.manager@warehouse.local | Manager@123 | DEL-03, BLR-04 |
| Clerk | clerk@warehouse.local | Clerk@1234 | PUN-01 |
| Clerk | mumbai.clerk@ / delhi.clerk@ / blr.clerk@warehouse.local | Clerk@1234 | one each |

To start over with fresh demo data, drop and recreate the database. Seeding runs only when there
are no users.

---

## Configuration (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/warehouse` | JDBC URL (`?sslmode=require` for Neon) |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | `postgres` / `postgres` | DB credentials |
| `DB_POOL_SIZE` | `5` | Hikari pool size (free tiers allow few connections) |
| `JWT_SECRET` | dev value | **Override in production**, at least 32 bytes |
| `JWT_EXPIRATION` | `PT12H` | Token lifetime |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Frontend origin(s), comma-separated |
| `AI_ENABLED` | `true` | Master switch for AI features |
| `AI_BASE_URL` | `http://localhost:11434/v1` | OpenAI-compatible base URL |
| `AI_API_KEY` | empty | Bearer token for hosted providers |
| `AI_CHAT_MODEL` | `llama3.1` | Model name |
| `AI_QUESTIONS_PER_MINUTE` | `10` | Per-user assistant rate limit |
| `DEMO_SEED_DATA` | `true` | Load demo data into an empty database |

Other tunables (retry attempts, forecast windows, service level) are in `application.yml`
under `app.*`.

---

## Deployment (free tiers)

1. **Database:** create a Postgres database on [Neon](https://neon.tech) and copy its JDBC URL.
2. **Backend:** on Render, create a New Blueprint from this repo (uses `render.yaml`). Fill in
   `DATABASE_*`, `CORS_ALLOWED_ORIGINS` and `AI_API_KEY` (a Gemini key).
3. **Frontend:** import `frontend/` into Vercel and set `VITE_API_BASE_URL` to the Render URL.
   `vercel.json` already rewrites routes for the SPA.

Free Render instances sleep when idle. The first request after a pause can take about 30 seconds,
and the UI says so. The daily job also runs at startup, to catch up on runs missed while asleep.

---

## API overview

All endpoints are under `/api` and need a Bearer token except `POST /api/auth/login`. Full,
interactive documentation is at `/swagger-ui.html`.

| Area | Endpoints |
|---|---|
| Auth | `POST auth/login`, `GET auth/me`, `POST auth/password` |
| Stock | `GET stock`, `GET stock/product/{id}`, `POST stock/issue`*, `POST stock/adjust`*, `PUT stock/settings`, `GET stock/consistency` |
| Ledger | `GET movements`, `POST movements/{id}/reverse`* |
| Transfers | `GET/POST transfers`*, `POST transfers/{id}/dispatch`*, `/receive`*, `/cancel` |
| Purchase orders | `GET/POST purchase-orders`*, `PUT purchase-orders/{id}`, `/place`, `/receive`*, `/cancel`, `/close` |
| Reorder | `GET reorder/suggestions`, `POST reorder/recompute`, `POST reorder/purchase-orders`* |
| Assistant | `POST assistant/ask`, `POST assistant/run`, `GET assistant/status`, `/history`, `/logs` |
| Catalogue | `products`, `suppliers`, `warehouses`, `users` (CRUD, role-guarded) |
| Reports | `GET dashboard` (home to-do list and stats), `GET dashboard/counts` (sidebar badges), `GET reports/valuation`, `GET reports/movers`, `POST jobs/daily` |

\* accepts an `Idempotency-Key` header.

Errors use RFC 7807 problem JSON: `{status, title, detail, code?, errors?}`. Business-rule
violations return `409` with a `code`, such as `INSUFFICIENT_STOCK`, `CONCURRENT_UPDATE` or
`ALREADY_REVERSED`.
