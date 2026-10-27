package com.warehouse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the AI Warehouse Inventory System backend.
 *
 * <p>The code base is organised by feature (account, warehouse, supplier, product, stock,
 * transfer, purchasing, forecast, assistant, dashboard, jobs). Each feature package follows the
 * same layering: {@code domain} (JPA entities + repositories), {@code service} (business rules,
 * transactions), {@code dto} (API contracts as records) and {@code web} (REST controllers).
 *
 * <p>All stock changes go through {@link com.warehouse.stock.service.StockLedgerService}, which
 * updates the stock level and appends a ledger entry in the same transaction.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class WarehouseApplication {

    public static void main(String[] args) {
        SpringApplication.run(WarehouseApplication.class, args);
    }
}
