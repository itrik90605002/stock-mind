package com.warehouse.seed;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates a believable 90-day operating history in memory: opening stock, daily issues that
 * follow each product's demand profile, automatic replenishment orders that arrive after the
 * supplier's lead time, transfers between warehouses and occasional damage/count adjustments.
 *
 * <p>Events for one product/warehouse pair are produced in time order (receipts, transfers in,
 * issues, transfers out, adjustments, ordering) and balances are tracked as they are produced,
 * so every {@code balanceAfter} is correct and the ledger sums to the final stock exactly.
 *
 * <p>A few pairs are deliberately "neglected" (no reordering in the last month) and rising or
 * spiking products use a reorder point sized for their old demand, so the demo shows realistic
 * stockout risks and reorder suggestions.
 */
final class HistorySimulator {

    static final int DAYS = 90;
    private static final int NEGLECTED_PAIRS = 10;

    // ------------------------------------------------------------ inputs

    record Pair(long productId, long warehouseId) {
    }

    record PairProfile(Pair pair, long supplierId, int supplierLeadDays, double meanDaily, double variability,
                       String trend, int reorderPoint, int orderUpTo) {
    }

    record Users(Map<Long, Long> clerkByWarehouse, Map<Long, Long> managerByWarehouse, long adminId) {
        long clerk(long warehouseId) {
            return clerkByWarehouse.getOrDefault(warehouseId, adminId);
        }

        long manager(long warehouseId) {
            return managerByWarehouse.getOrDefault(warehouseId, adminId);
        }
    }

    // ------------------------------------------------------------ outputs

    /** A ledger entry; {@code docRef} points into {@link #orders} or {@link #transfers} by index. */
    record Movement(Instant at, long productId, long warehouseId, String type, int quantity, int balanceAfter,
                    String reason, String referenceType, Integer docRef, long userId) {
    }

    static final class Order {
        final long supplierId;
        final long warehouseId;
        final Instant createdAt;
        final LocalDate expectedDate;
        final long createdBy;
        final Map<Long, int[]> lines = new LinkedHashMap<>(); // productId -> {ordered, received}
        String status = "ORDERED";
        Instant closedAt;
        String note;
        LocalDate arrivalDay;

        Order(long supplierId, long warehouseId, Instant createdAt, LocalDate expectedDate, long createdBy) {
            this.supplierId = supplierId;
            this.warehouseId = warehouseId;
            this.createdAt = createdAt;
            this.expectedDate = expectedDate;
            this.createdBy = createdBy;
        }
    }

    static final class Transfer {
        final long sourceId;
        final long destinationId;
        final Instant createdAt;
        final long createdBy;
        final Map<Long, int[]> lines = new LinkedHashMap<>(); // productId -> {quantity, received or -1}
        String status;
        Instant dispatchedAt;
        Instant receivedAt;
        LocalDate arrivalDay;
        String note;

        Transfer(long sourceId, long destinationId, Instant createdAt, long createdBy) {
            this.sourceId = sourceId;
            this.destinationId = destinationId;
            this.createdAt = createdAt;
            this.createdBy = createdBy;
        }
    }

    final List<Movement> movements = new ArrayList<>();
    final List<Order> orders = new ArrayList<>();
    final List<Transfer> transfers = new ArrayList<>();
    final Map<Pair, Integer> onHand = new HashMap<>();
    final Map<Pair, Integer> reserved = new HashMap<>();
    final Map<Pair, Integer> reorderPoints = new HashMap<>();

    // ------------------------------------------------------------ state

    private final List<PairProfile> profiles;
    private final Users users;
    private final Instant now;
    private final LocalDate today;
    private final LocalDate start;
    private final Random rnd = new Random(20261027L);
    private final Map<Pair, Integer> pendingOrder = new HashMap<>();       // pair -> order index
    private final Map<Pair, Boolean> neglected = new HashMap<>();

    HistorySimulator(List<PairProfile> profiles, Users users, Instant now) {
        this.profiles = profiles;
        this.users = users;
        this.now = now;
        this.today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        this.start = today.minusDays(DAYS);
    }

    void run() {
        pickNeglectedPairs();
        profiles.forEach(p -> reorderPoints.put(p.pair(), p.reorderPoint()));

        // Opening balances on day 0.
        for (PairProfile p : profiles) {
            int opening = p.orderUpTo() + rnd.nextInt(Math.max(1, p.orderUpTo() / 4));
            apply(p.pair(), at(start, 7, 0), "RECEIPT", opening, "Opening balance", "OPENING", null,
                    users.manager(p.pair().warehouseId()));
        }

        for (int d = 0; d <= DAYS; d++) {
            LocalDate day = start.plusDays(d);
            receiveOrders(day);
            receiveTransfers(day);
            for (PairProfile p : profiles) {
                issue(p, day, d);
            }
            if (d >= 8 && (d % 4 == 2 || d == DAYS - 1)) { // the last one is still in transit today
                rebalance(day);
            }
            for (PairProfile p : profiles) {
                adjustOccasionally(p, day);
                reorderIfNeeded(p, day, d);
            }
        }
        finishingTouches();
    }

    // ------------------------------------------------------------ daily steps

    private void issue(PairProfile p, LocalDate day, int d) {
        double mean = p.meanDaily() * trendMultiplier(p.trend(), d) * weekdayMultiplier(day);
        int demand = (int) Math.round(Math.max(0, mean * (1 + p.variability() * rnd.nextGaussian())));
        if (demand == 0) {
            return;
        }
        int parts = demand >= 6 && rnd.nextBoolean() ? 2 : 1;
        int remaining = demand;
        for (int i = 0; i < parts; i++) {
            int qty = i == parts - 1 ? remaining : remaining / 2;
            remaining -= qty;
            int available = onHand.getOrDefault(p.pair(), 0) - reserved.getOrDefault(p.pair(), 0);
            qty = Math.min(qty, available);
            if (qty <= 0) {
                return; // stocked out: unmet demand is simply not recorded
            }
            apply(p.pair(), at(day, 11 + i * 3, rnd.nextInt(50)), "ISSUE", -qty,
                    rnd.nextInt(4) == 0 ? "Order picking - wave " + (1 + rnd.nextInt(6)) : "Customer order dispatch",
                    "MANUAL", null, users.clerk(p.pair().warehouseId()));
        }
    }

    private void adjustOccasionally(PairProfile p, LocalDate day) {
        if (rnd.nextDouble() > 0.006) {
            return;
        }
        int stock = onHand.getOrDefault(p.pair(), 0) - reserved.getOrDefault(p.pair(), 0);
        boolean damage = rnd.nextInt(3) > 0;
        if (damage && stock >= 3) {
            int qty = 1 + rnd.nextInt(3);
            apply(p.pair(), at(day, 17, 30), "ADJUSTMENT", -qty,
                    rnd.nextBoolean() ? "Damaged in handling" : "Water damage found during inspection",
                    "MANUAL", null, users.clerk(p.pair().warehouseId()));
        } else if (!damage) {
            int qty = (1 + rnd.nextInt(3)) * (rnd.nextBoolean() || stock < 3 ? 1 : -1);
            apply(p.pair(), at(day, 17, 30), "ADJUSTMENT", qty, "Cycle count correction", "MANUAL", null,
                    users.clerk(p.pair().warehouseId()));
        }
    }

    private void reorderIfNeeded(PairProfile p, LocalDate day, int d) {
        if (pendingOrder.containsKey(p.pair()) || !isReviewDay(day)) {
            return;
        }
        if (neglected.getOrDefault(p.pair(), false) && d >= DAYS - 32) {
            return; // nobody re-ordered this one for a month
        }
        int stock = onHand.getOrDefault(p.pair(), 0);
        if (stock > p.reorderPoint()) {
            return;
        }
        Instant orderedAt = at(day, 18, 0);
        if (orderedAt.isAfter(now)) {
            return;
        }
        int qty = roundUp(p.orderUpTo() - stock, 5);
        int actualLead = Math.max(1, p.supplierLeadDays() + rnd.nextInt(4) - 1);
        Order order = findOrCreateOrder(p, day, orderedAt);
        order.lines.put(p.pair().productId(), new int[]{qty, 0});
        if (order.arrivalDay == null || day.plusDays(actualLead).isAfter(order.arrivalDay)) {
            order.arrivalDay = day.plusDays(actualLead);
        }
        pendingOrder.put(p.pair(), orders.indexOf(order));
    }

    private Order findOrCreateOrder(PairProfile p, LocalDate day, Instant orderedAt) {
        for (int i = orders.size() - 1; i >= 0; i--) {
            Order o = orders.get(i);
            if (o.supplierId == p.supplierId() && o.warehouseId == p.pair().warehouseId()
                    && LocalDate.ofInstant(o.createdAt, ZoneOffset.UTC).equals(day)) {
                return o;
            }
        }
        Order order = new Order(p.supplierId(), p.pair().warehouseId(), orderedAt,
                day.plusDays(p.supplierLeadDays()), users.manager(p.pair().warehouseId()));
        order.note = "Auto-replenishment";
        orders.add(order);
        return order;
    }

    private void receiveOrders(LocalDate day) {
        for (int i = 0; i < orders.size(); i++) {
            Order o = orders.get(i);
            if (!"ORDERED".equals(o.status) || !day.equals(o.arrivalDay)) {
                continue;
            }
            Instant receivedAt = at(day, 8, 30 + rnd.nextInt(25));
            if (receivedAt.isAfter(now)) {
                continue;
            }
            // About one in twelve recent deliveries arrives short and stays partially received.
            boolean partial = day.isAfter(today.minusDays(12)) && rnd.nextInt(12) == 0;
            for (Map.Entry<Long, int[]> line : o.lines.entrySet()) {
                int qty = partial ? Math.max(1, line.getValue()[0] * 6 / 10) : line.getValue()[0];
                line.getValue()[1] = qty;
                Pair pair = new Pair(line.getKey(), o.warehouseId);
                apply(pair, receivedAt, "RECEIPT", qty, "PO from supplier", "PURCHASE_ORDER", i,
                        users.clerk(o.warehouseId));
                pendingOrder.remove(pair);
            }
            o.status = partial ? "PARTIALLY_RECEIVED" : "RECEIVED";
            o.closedAt = partial ? null : receivedAt;
            if (partial) {
                o.note = "Supplier back-ordered the remainder";
            }
        }
    }

    private void receiveTransfers(LocalDate day) {
        for (int i = 0; i < transfers.size(); i++) {
            Transfer t = transfers.get(i);
            if (!"DISPATCHED".equals(t.status) || !day.equals(t.arrivalDay)) {
                continue;
            }
            Instant receivedAt = at(day, 10, 15);
            if (receivedAt.isAfter(now)) {
                continue;
            }
            for (Map.Entry<Long, int[]> line : t.lines.entrySet()) {
                int qty = line.getValue()[0];
                line.getValue()[1] = qty;
                apply(new Pair(line.getKey(), t.destinationId), receivedAt, "TRANSFER_IN", qty,
                        "Transfer #" + (i + 1) + " received", "TRANSFER", i, users.clerk(t.destinationId));
            }
            t.status = "RECEIVED";
            t.receivedAt = receivedAt;
        }
    }

    /** Moves stock from the warehouse with the most cover to the one with the least, for one product. */
    private void rebalance(LocalDate day) {
        Map<Long, List<PairProfile>> byProduct = new HashMap<>();
        profiles.forEach(p -> byProduct.computeIfAbsent(p.pair().productId(), k -> new ArrayList<>()).add(p));
        List<List<PairProfile>> candidates = byProduct.values().stream().filter(l -> l.size() >= 2).toList();
        List<PairProfile> group = null;
        double bestGap = 0;
        for (int i = 0; i < 25; i++) {
            List<PairProfile> g = candidates.get(rnd.nextInt(candidates.size()));
            double gap = g.stream().mapToDouble(this::cover).max().orElse(0) - g.stream().mapToDouble(this::cover).min().orElse(0);
            if (gap > bestGap) {
                bestGap = gap;
                group = g;
            }
        }
        if (group == null) {
            return;
        }
        PairProfile richest = group.stream().max(Comparator.comparingDouble(this::cover)).orElseThrow();
        PairProfile poorest = group.stream().min(Comparator.comparingDouble(this::cover)).orElseThrow();
        int sourceAvailable = onHand.get(richest.pair()) - reserved.getOrDefault(richest.pair(), 0);
        int qty = roundUp(Math.max(5, (int) (poorest.meanDaily() * 7)), 5);
        if (richest == poorest || qty > sourceAvailable / 2) {
            return;
        }
        Instant dispatchedAt = at(day, 16, 30);
        if (dispatchedAt.isAfter(now)) {
            return; // never create a document whose stock movement would be in the future
        }
        Transfer t = new Transfer(richest.pair().warehouseId(), poorest.pair().warehouseId(), at(day, 9, 45),
                users.manager(richest.pair().warehouseId()));
        t.lines.put(richest.pair().productId(), new int[]{qty, -1});
        t.status = "DISPATCHED";
        t.dispatchedAt = dispatchedAt;
        t.arrivalDay = day.plusDays(2);
        t.note = "Stock rebalancing";
        transfers.add(t);
        apply(richest.pair(), dispatchedAt, "TRANSFER_OUT", -qty, "Transfer #" + transfers.size() + " dispatched",
                "TRANSFER", transfers.size() - 1, users.clerk(richest.pair().warehouseId()));
    }

    /** Current documents that make the demo interesting: drafts, an in-transit transfer, a cancelled order. */
    private void finishingTouches() {
        // A draft transfer reserving stock (shows "reserved" and the dispatch workflow).
        PairProfile big = profiles.stream()
                .filter(p -> onHand.getOrDefault(p.pair(), 0) > 60)
                .max(Comparator.comparingInt(p -> onHand.get(p.pair()))).orElse(null);
        if (big != null) {
            long destination = profiles.stream().map(p -> p.pair().warehouseId())
                    .filter(w -> w != big.pair().warehouseId()).findFirst().orElse(big.pair().warehouseId());
            if (destination != big.pair().warehouseId()) {
                Transfer t = new Transfer(big.pair().warehouseId(), destination, now.minusSeconds(3600),
                        users.manager(big.pair().warehouseId()));
                int qty = 20;
                t.lines.put(big.pair().productId(), new int[]{qty, -1});
                t.status = "DRAFT";
                t.note = "Top-up for upcoming promotion";
                transfers.add(t);
                reserved.merge(big.pair(), qty, Integer::sum);
            }
        }
        // A cancelled order from last month.
        if (!profiles.isEmpty()) {
            PairProfile p = profiles.get(rnd.nextInt(profiles.size()));
            Order o = new Order(p.supplierId(), p.pair().warehouseId(), at(today.minusDays(25), 12, 0),
                    today.minusDays(18), users.manager(p.pair().warehouseId()));
            o.lines.put(p.pair().productId(), new int[]{50, 0});
            o.status = "CANCELLED";
            o.closedAt = at(today.minusDays(24), 10, 0);
            o.note = "Cancelled: supplier price increase, sourcing elsewhere";
            orders.add(o);
        }
    }

    // ------------------------------------------------------------ helpers

    private void apply(Pair pair, Instant at, String type, int qty, String reason, String referenceType,
                       Integer docRef, long userId) {
        if (at.isAfter(now) || qty == 0) {
            return;
        }
        int balance = onHand.getOrDefault(pair, 0) + qty;
        if (balance < 0) {
            throw new IllegalStateException("Simulation produced negative stock for " + pair);
        }
        onHand.put(pair, balance);
        movements.add(new Movement(at, pair.productId(), pair.warehouseId(), type, qty, balance, reason, referenceType,
                docRef, userId));
    }

    /** Buyers review stock twice a week, so each order bundles several products per supplier. */
    private static boolean isReviewDay(LocalDate day) {
        return day.getDayOfWeek() == java.time.DayOfWeek.MONDAY || day.getDayOfWeek() == java.time.DayOfWeek.THURSDAY;
    }

    private double cover(PairProfile p) {
        return (onHand.getOrDefault(p.pair(), 0) - reserved.getOrDefault(p.pair(), 0)) / Math.max(0.1, p.meanDaily());
    }

    private void pickNeglectedPairs() {
        List<PairProfile> pool = new ArrayList<>(profiles.stream()
                .filter(p -> !"falling".equals(p.trend()) && p.meanDaily() >= 2).toList());
        for (int i = 0; i < NEGLECTED_PAIRS && !pool.isEmpty(); i++) {
            neglected.put(pool.remove(rnd.nextInt(pool.size())).pair(), true);
        }
    }

    private static double trendMultiplier(String trend, int day) {
        double t = (double) day / DAYS;
        return switch (trend) {
            case "rising" -> 0.7 + 0.8 * t;
            case "falling" -> 1.3 - 0.7 * t;
            case "spike" -> day > DAYS - 14 ? 1.9 : 1.0;
            default -> 1.0;
        };
    }

    private static double weekdayMultiplier(LocalDate day) {
        return switch (day.getDayOfWeek()) {
            case SUNDAY -> 0.35;
            case SATURDAY -> 0.7;
            default -> 1.1;
        };
    }

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(LocalTime.of(Math.min(hour, 23), Math.min(minute, 59))).toInstant(ZoneOffset.UTC);
    }

    private static int roundUp(int value, int step) {
        return Math.max(step, ((value + step - 1) / step) * step);
    }
}
