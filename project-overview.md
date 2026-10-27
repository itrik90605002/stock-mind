# Warehouse Inventory System with AI Assistant

**Actors:** Admin, Warehouse Manager, Clerk, System, AI Service

---

## MVP Features

### Accounts & Roles
- Users log in with email and password.
- Admins can create Manager and Clerk accounts, assign each one to one or more warehouses, deactivate them (with a reason), and reactivate them.
- Managers can see and act on every warehouse they are assigned to. Clerks can record stock movements only in their assigned warehouses.
- Admins can see all warehouses.

### Warehouses
- Admins can add and edit warehouses (code, name, address).
- Admins can deactivate a warehouse only when it holds no stock and has no open transfers or purchase orders.

### Products
- Managers can add products with SKU, name, category, unit of measure (pcs, kg, box…), unit cost, and description.
- Each product has a reorder point and a lead time in days for each warehouse.
- Managers can edit product details and archive products that are no longer stocked.
- Users can search products by SKU, name, or category, and filter by warehouse.

### Suppliers
- Managers can add suppliers (name, contact email, phone, default lead time).

### Stock Levels
- Users can see on-hand, reserved, and available quantity for every product in every warehouse.
  - Available = on hand − reserved (stock held for outgoing transfers).
- The stock page highlights items at or below their reorder point.
- Stock can never go below zero.

### Stock Movements (the ledger)
- Every change to stock is recorded as one movement in the ledger: Receipt, Issue (goods out), Adjustment (count correction, damage, loss), Transfer Out, or Transfer In.
- Each movement records the product, warehouse, quantity change, resulting balance, reason, user, time, and the document that caused it (purchase order, transfer, or manual).
- Ledger entries are never edited or deleted. A mistake is fixed with a reversing entry.
- Clerks can record manual issues and adjustments. Adjustments need a reason.
- Managers can reverse a manual movement once. The reversal is a new entry that points to the original.
- Users can view the ledger filtered by product, warehouse, movement type, and date range.
- Managers can run a consistency check that rebuilds every stock level from the ledger and reports any mismatch.

### Concurrent Updates (consistency)
- Two users updating the same stock at the same time can never cause a lost update or negative stock.
  - Stock rows use optimistic locking (a version column). A conflicting update is retried automatically a few times, then reported to the user.
  - The database also enforces `on_hand >= 0` and `reserved <= on_hand`.
- The stock change and its ledger entry are saved in one transaction: either both happen or neither does.
- Every stock-changing request can carry an idempotency key, so a double-click or a network retry never records the same movement twice.

### Transfers Between Warehouses
- Managers can create a transfer from one warehouse to another with one or more product lines.
- Lifecycle: **Draft → Dispatched → Received** (or **Cancelled**).
  - Creating a draft reserves the stock at the source, so it cannot be issued or promised twice. It fails if not enough stock is available.
  - Dispatching reduces stock at the source (Transfer Out), using up the reservation, and puts the goods in transit.
  - Receiving adds stock at the destination (Transfer In). Short receipts are allowed. The shortfall is recorded on the transfer line as lost in transit.
  - A draft can be cancelled, which releases the reservation. A dispatched transfer cannot be cancelled, only received.
- Users can see stock that is in transit.

### Purchase Orders
- Managers can create a purchase order for a supplier and a destination warehouse with product lines (quantity, unit cost).
- Lifecycle: **Draft → Ordered → Partially Received → Received** (or **Cancelled** before any receipt).
- Managers can edit a draft, and can close a partially received order short when the rest will not arrive.
- Clerks can receive a purchase order in full or in part. Each receipt adds stock (Receipt movements).
- More than the remaining ordered quantity cannot be received.
- Users can see open purchase orders and the quantities still expected.

### AI Assistant: Ask in Plain English
- Users can ask questions like:
  - "Which items will run out next week?"
  - "How many units of SKU-1042 are in the Pune warehouse?"
  - "What did we receive from Acme last month?"
  - "Show the biggest stock adjustments this quarter."
- Safe handling of questions:
  - The AI never writes or runs SQL. It turns the question into a structured **query plan**: one of a fixed set of query types (stock lookup, stockout forecast, movement history, purchase order status, transfer status, top movers) with validated filters (product, warehouse, supplier, date range, limit).
  - The backend validates the plan against an allow-list, applies the user's warehouse permissions, caps the number of rows, and runs prewritten, parameterized queries in a read-only transaction.
  - Questions that are off-topic or that ask to change data are refused with a short explanation.
  - The answer shows the interpreted plan ("Showing: stockout forecast · all your warehouses · next 7 days") so the user can check what was understood.
- If the AI service is unavailable, the assistant says so, and quick-question buttons (fixed plans, no AI needed) and all other pages keep working.
- Questions are rate-limited per user.
- Every question, its plan, and the outcome are logged for review.

### AI Assistant: Reorder Suggestions
- For each product and warehouse, the System calculates:
  - average daily usage from the last 30/90 days of Issue and Transfer Out movements,
  - days of stock left at that rate,
  - a suggested reorder quantity = (usage × (lead time + review period)) + safety stock − (available + incoming on open purchase orders).
- The numbers come from this formula, not from the AI. The AI writes a one-line explanation of each suggestion ("Usage up 40% in the last 2 weeks; 5 days of stock left, 7-day lead time").
- Managers can turn selected suggestions into draft purchase orders in one click. One draft is created per supplier and warehouse.

### Dashboard & Reports
- Home: a role-aware to-do list (items to reorder, draft orders to place, deliveries to receive with late ones first, transfers to dispatch or receive), key figures, and recent movements.
- Stock valuation per warehouse (on hand × unit cost).
- Top moving and slow moving products (last 30 days).

### System
- A daily job refreshes usage statistics and reorder suggestions and clears expired idempotency keys. It also runs at startup, and an Admin can trigger it.
- Demo data is seeded so the AI features have something to work with. Master data comes from JSON files in `backend/src/main/resources/seed/`: 4 warehouses, 10 suppliers, 120 products and 7 users. On top of that, 90 days of history is simulated: about 46k ledger entries, about 400 purchase orders and about 20 transfers.

---

## Suggested Features (Later)
- Refresh tokens, password reset by email.
- Forecasts that correct for stockout days (usage is under-estimated while an item is out of stock).
- Automated test suite (unit tests for `ReorderMath`, plus concurrency integration tests against Postgres).
- Bin and shelf locations inside a warehouse.
- Batch/lot numbers and expiry dates (FEFO picking).
- Sales orders and customer shipments.
- Barcode scanning with the device camera.
- Email alerts for low stock.
- Seasonality-aware forecasting.
- CSV import and export of products and stock counts.
- Cycle counting workflow.
- Charts for stock and usage trends.

---

## Out of Scope
Real supplier integrations (EDI), accounting/invoicing, multi-currency, mobile app.
