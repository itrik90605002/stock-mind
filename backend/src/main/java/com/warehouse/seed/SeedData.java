package com.warehouse.seed;

import java.math.BigDecimal;
import java.util.List;

/** Shapes of the JSON files under {@code src/main/resources/seed/}. */
final class SeedData {

    private SeedData() {
    }

    /** @param demandFactor multiplies every product's base usage in this warehouse */
    record WarehouseSeed(String code, String name, String address, double demandFactor) {
    }

    record SupplierSeed(String name, String contactEmail, String phone, int leadTimeDays) {
    }

    record UserSeed(String email, String password, String fullName, String role, List<String> warehouses) {
    }

    /**
     * @param demand drives the simulated history; not stored on the product
     */
    record ProductSeed(String sku, String name, String category, String unit, BigDecimal unitCost, String supplier,
                       String description, Demand demand) {
    }

    /**
     * @param baseDailyUsage average units issued per day in a warehouse with demand factor 1.0
     * @param variability    day-to-day noise as a fraction of the mean (0.2 = calm, 0.6 = erratic)
     * @param trend          stable | rising | falling | spike (demand jump in the last two weeks)
     * @param warehouses     codes of the warehouses that stock the product
     */
    record Demand(double baseDailyUsage, double variability, String trend, List<String> warehouses) {
    }
}
