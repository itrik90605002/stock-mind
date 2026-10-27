# Project overview

**Who uses it:** Admin (everything), Manager (orders, transfers and reports for their warehouses), Clerk (receives and issues stock in their warehouses).

## The flow

```
Suppliers ──purchase order──▶ Warehouse ──transfer──▶ Another warehouse
                                 │
                                 └──issue──▶ sold / used
```

1. **What to reorder** suggests quantities from recent usage.
2. **Purchase order:** one supplier, one destination warehouse. Draft → Ordered → Partly received → Received (or Cancelled, or closed short).
3. **Receiving** a delivery adds the stock straight to that warehouse. Partial deliveries are allowed.
4. **Transfer:** Draft (reserves stock at the source) → Dispatched → Received. Any shortfall is recorded as lost in transit.
5. **Issue** records goods leaving the business. **Adjust** corrects counts, damage and loss, and needs a reason.

## Screens

| Menu | What it's for |
|---|---|
| Home | Headline numbers, your to-do list, 30-day in/out chart |
| Ask AI | Questions in plain English |
| Stock | Tabs: **Products** (catalogue) and **Stock levels** (per warehouse). A product page shows stock everywhere, actions and history. |
| What to reorder · Purchase orders · Suppliers | Buying |
| Transfers · Movement history | Moving stock and the full audit log |
| Reports | Stock value, fast and slow movers (Managers, Admins) |
| Users & warehouses | Admins |

## How stock stays correct

- **No lost updates:** stock rows are version-checked. A conflicting update is retried automatically.
- **No negative stock:** the database itself rejects it.
- **Every change is logged:** stock changes and their ledger entries save together. Ledger entries can never be edited or deleted. Mistakes are fixed with a reversing entry.
- **No double submits:** a double click or a network retry never records a movement twice.
- **Consistency check:** rebuilds every stock level from the ledger and reports any mismatch.

## Ask AI

AI is used only here. One model call turns a question into two answers, shown one after the other:

- **System-built:** the model picks one of 9 prewritten queries and fills in filters. The backend validates them and runs its own SQL.
- **AI-built:** the model writes its own `SELECT`. It runs as a read-only database role that can see only a few views (no users or passwords), limited to the user's warehouses, with a 5-second timeout and a row cap.

The model never sees your data, only the question and the view definitions. Reorder suggestions don't use AI: quantities and their one-line "why" come from a formula. If AI is down, the quick-question buttons and every other page still work.

## Not included (by choice)

Sales orders and customers, supplier and customer returns, stock-count rounds, multiple suppliers per product, invoices and payments, bin locations, batches and expiry, barcode scanning.
