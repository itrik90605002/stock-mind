package com.warehouse.assistant.plan;

/**
 * The fixed set of questions the assistant can answer. Each maps to one prewritten, parameterised
 * query in {@link com.warehouse.assistant.query.AssistantQueryExecutor}; the model can only choose
 * among these and fill in filters.
 */
public enum QueryIntent {
    STOCK_LOOKUP("Stock levels"),
    LOW_STOCK("Items at or below reorder point"),
    STOCKOUT_FORECAST("Stockout forecast"),
    MOVEMENT_HISTORY("Stock movements"),
    TOP_MOVERS("Product movers"),
    PURCHASE_ORDER_STATUS("Purchase orders"),
    TRANSFER_STATUS("Transfers"),
    REORDER_SUGGESTIONS("Reorder suggestions"),
    STOCK_VALUE("Stock value"),
    /** Not a supported question (off-topic, or a request to change data). */
    UNSUPPORTED("Unsupported");

    private final String label;

    QueryIntent(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
