package com.warehouse.assistant.query;

/**
 * Describes one result column so the frontend can format values without guessing.
 *
 * @param type one of {@code text, number, decimal, money, date, datetime, status}
 */
public record Column(String key, String label, String type) {

    public static Column text(String key, String label) {
        return new Column(key, label, "text");
    }

    public static Column number(String key, String label) {
        return new Column(key, label, "number");
    }

    public static Column decimal(String key, String label) {
        return new Column(key, label, "decimal");
    }

    public static Column money(String key, String label) {
        return new Column(key, label, "money");
    }

    public static Column date(String key, String label) {
        return new Column(key, label, "date");
    }

    public static Column datetime(String key, String label) {
        return new Column(key, label, "datetime");
    }

    public static Column status(String key, String label) {
        return new Column(key, label, "status");
    }
}
