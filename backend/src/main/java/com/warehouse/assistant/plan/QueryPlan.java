package com.warehouse.assistant.plan;

/**
 * An <em>untrusted</em> query plan, exactly as produced by the model (or sent by a quick-query
 * button). Every field is free text or a loose number; nothing here is used until
 * {@link PlanValidator} has checked it against allow-lists and resolved names to ids.
 *
 * @param intent        one of {@link QueryIntent}
 * @param product       SKU or part of a product name
 * @param warehouse     warehouse code or name
 * @param movementType  one of the ledger movement types
 * @param status        purchase order or transfer status, or OPEN
 * @param fromDate      ISO date (inclusive)
 * @param toDate        ISO date (inclusive)
 * @param horizonDays   look-ahead for forecasts
 * @param direction     FAST or SLOW, for movers
 * @param sortBy        RECENT or LARGEST, for movement history
 * @param refusalReason why the question is unsupported (intent UNSUPPORTED only)
 */
public record QueryPlan(String intent, String product, String category, String warehouse, String supplier,
                        String movementType, String status, String fromDate, String toDate, Integer horizonDays,
                        String direction, String sortBy, Integer limit, String refusalReason) {
}
