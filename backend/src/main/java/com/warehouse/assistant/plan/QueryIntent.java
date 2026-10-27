package com.warehouse.assistant.plan;

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
    UNSUPPORTED("Unsupported");

    private final String label;

    QueryIntent(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
