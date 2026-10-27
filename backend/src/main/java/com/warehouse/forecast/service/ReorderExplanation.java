package com.warehouse.forecast.service;

import java.util.Locale;

final class ReorderExplanation {

    record Facts(String unit, double avgDailyUsage, Double trendPct, Double daysOfCover, int leadTimeDays,
                 int available, int incoming, int safetyStock, int suggestedQty) {
    }

    private ReorderExplanation() {
    }

    static String of(Facts f, int reviewPeriodDays) {
        StringBuilder sb = new StringBuilder();
        if (f.available() <= 0) {
            sb.append("Out of stock");
            sb.append(String.format(Locale.ROOT, " (recent usage ~%s %s/day)", number(f.avgDailyUsage()), f.unit()));
        } else {
            sb.append(String.format(Locale.ROOT, "Uses ~%s %s/day", number(f.avgDailyUsage()), f.unit()));
            if (f.trendPct() != null && Math.abs(f.trendPct()) >= 10) {
                sb.append(String.format(Locale.ROOT, " (%s %.0f%% vs prior 2 weeks)", f.trendPct() > 0 ? "up" : "down",
                        Math.abs(f.trendPct())));
            }
            if (f.daysOfCover() != null) {
                sb.append(String.format(Locale.ROOT, "; %s days of stock left vs %d-day lead time",
                        number(f.daysOfCover()), f.leadTimeDays()));
            } else {
                sb.append(String.format(Locale.ROOT, "; %d available, at or below reorder point", f.available()));
            }
        }
        if (f.incoming() > 0) {
            sb.append(String.format(Locale.ROOT, "; %d already on order", f.incoming()));
        }
        sb.append(String.format(Locale.ROOT, ". Order %d to cover %d days plus %d safety stock.",
                f.suggestedQty(), f.leadTimeDays() + reviewPeriodDays, f.safetyStock()));
        return sb.toString();
    }

    private static String number(double value) {
        return value >= 10 ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }
}
