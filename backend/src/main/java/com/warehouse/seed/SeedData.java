package com.warehouse.seed;

import java.math.BigDecimal;
import java.util.List;

final class SeedData {

    private SeedData() {
    }

    record WarehouseSeed(String code, String name, String address) {
    }

    record SupplierSeed(String name, String contactEmail, String phone, int leadTimeDays) {
    }

    record UserSeed(String email, String password, String fullName, String role, List<String> warehouses) {
    }

    record ProductSeed(String sku, String name, String category, String unit, BigDecimal unitCost, String supplier,
                       String description, Demand demand) {
    }

    record Demand(double variability, String trend) {
    }

    record WarehouseStockSeed(String warehouse, String name, List<StockSeed> stock) {
    }

    record StockSeed(String sku, String product, int openingStock, int reorderPoint, double dailyUsage) {
    }
}
