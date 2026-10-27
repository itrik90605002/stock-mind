package com.warehouse.assistant.plan;

/**
 * An <em>untrusted</em> query plan, exactly as produced by the model (or sent by a quick-query
 * button). Every field is free text or a loose number; nothing here is used until
 * {@link PlanValidator} has checked it against allow-lists and resolved names to ids.
 */
public record QueryPlan(String intent, String product, String category, String warehouse, String supplier,
                        String movementType, String status, String fromDate, String toDate, Integer horizonDays,
                        String direction, String sortBy, Integer limit, String refusalReason) {
}
