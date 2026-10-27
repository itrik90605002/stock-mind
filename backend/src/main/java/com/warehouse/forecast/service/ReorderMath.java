package com.warehouse.forecast.service;

import com.warehouse.config.AppProperties;

/**
 * Pure reorder arithmetic (no I/O), so the numbers behind every suggestion are reproducible.
 *
 * <pre>
 *   avg daily usage  = 0.6 x mean(last short window) + 0.4 x mean(long window)
 *   safety stock     = z x stddev(daily usage, short window) x sqrt(lead time)
 *   reorder trigger  = max(reorder point, avg x lead time + safety stock)
 *   order-up-to      = max(avg x (lead time + review period) + safety stock, reorder point)
 *   stock position   = available + incoming (open purchase orders)
 *   suggested qty    = order-up-to - stock position, when stock position <= reorder trigger; else 0
 * </pre>
 *
 * Weighting the recent window reacts to rising demand without overreacting to a single spike.
 */
public final class ReorderMath {

    static final int TREND_WINDOW_DAYS = 14;
    private static final double RECENT_WEIGHT = 0.6;

    private ReorderMath() {
    }

    /**
     * @param dailyUsage   outbound units per day over the long window, oldest first
     * @param available    on hand minus reserved
     * @param incoming     units still expected on open purchase orders
     * @param reorderPoint manager-set minimum
     * @param leadTimeDays days from ordering to delivery
     */
    public record Inputs(int[] dailyUsage, int available, int incoming, int reorderPoint, int leadTimeDays) {
    }

    /**
     * @param trendPct    last 14 days vs the 14 days before, percent; null without a baseline
     * @param daysOfCover available / avg daily usage; null when there is no usage
     */
    public record Result(double avgDailyUsage, double stddev, Double trendPct, int safetyStock, Double daysOfCover,
                         int reorderTrigger, int orderUpTo, int suggestedQty) {
    }

    public static Result compute(Inputs in, AppProperties.Forecast settings) {
        int[] usage = in.dailyUsage();
        int shortWindow = Math.min(settings.shortWindowDays(), usage.length);

        double longMean = mean(usage, 0, usage.length);
        double shortMean = mean(usage, usage.length - shortWindow, usage.length);
        double avg = RECENT_WEIGHT * shortMean + (1 - RECENT_WEIGHT) * longMean;
        double stddev = stddev(usage, usage.length - shortWindow, usage.length, shortMean);

        int lead = in.leadTimeDays();
        int safety = (int) Math.ceil(settings.serviceLevelZ() * stddev * Math.sqrt(lead));
        int trigger = Math.max(in.reorderPoint(), (int) Math.ceil(avg * lead) + safety);
        int orderUpTo = Math.max((int) Math.ceil(avg * (lead + settings.reviewPeriodDays())) + safety, in.reorderPoint());
        int position = in.available() + in.incoming();
        int suggested = position <= trigger ? Math.max(orderUpTo - position, 0) : 0;

        Double cover = avg > 0 ? in.available() / avg : null;
        return new Result(avg, stddev, trend(usage), safety, cover, trigger, orderUpTo, suggested);
    }

    private static Double trend(int[] usage) {
        if (usage.length < 2 * TREND_WINDOW_DAYS) {
            return null;
        }
        double recent = mean(usage, usage.length - TREND_WINDOW_DAYS, usage.length);
        double prior = mean(usage, usage.length - 2 * TREND_WINDOW_DAYS, usage.length - TREND_WINDOW_DAYS);
        return prior > 0 ? (recent - prior) / prior * 100.0 : null;
    }

    private static double mean(int[] values, int from, int to) {
        if (to <= from) {
            return 0;
        }
        long sum = 0;
        for (int i = from; i < to; i++) {
            sum += values[i];
        }
        return (double) sum / (to - from);
    }

    private static double stddev(int[] values, int from, int to, double mean) {
        if (to - from < 2) {
            return 0;
        }
        double squares = 0;
        for (int i = from; i < to; i++) {
            squares += (values[i] - mean) * (values[i] - mean);
        }
        return Math.sqrt(squares / (to - from));
    }
}
