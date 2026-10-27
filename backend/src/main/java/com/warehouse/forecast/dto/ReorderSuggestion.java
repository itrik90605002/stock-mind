package com.warehouse.forecast.dto;

import com.warehouse.forecast.domain.StockForecast;
import com.warehouse.product.domain.Product;
import com.warehouse.product.dto.ProductRef;
import com.warehouse.warehouse.dto.WarehouseRef;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A suggested purchase for one product in one warehouse. The quantity is computed by formula;
 * {@code explanation} is a one-line rationale written by AI when available ({@code explanationByAi}),
 * otherwise generated from the same numbers.
 */
public record ReorderSuggestion(Long id, ProductRef product, String category, WarehouseRef warehouse,
                                Long supplierId, String supplierName, BigDecimal unitCost,
                                BigDecimal avgDailyUsage, BigDecimal trendPct, int leadTimeDays, int safetyStock,
                                int available, int incoming, BigDecimal daysOfCover, int suggestedQty,
                                BigDecimal estimatedCost, String explanation, boolean explanationByAi,
                                Instant computedAt) {

    public static ReorderSuggestion from(StockForecast f) {
        Product p = f.getProduct();
        return new ReorderSuggestion(f.getId(), ProductRef.from(p), p.getCategory(), WarehouseRef.from(f.getWarehouse()),
                p.getSupplier() == null ? null : p.getSupplier().getId(),
                p.getSupplier() == null ? null : p.getSupplier().getName(), p.getUnitCost(), f.getAvgDailyUsage(),
                f.getTrendPct(), f.getLeadTimeDays(), f.getSafetyStock(), f.getAvailable(), f.getIncoming(),
                f.getDaysOfCover(), f.getSuggestedQty(), p.getUnitCost().multiply(BigDecimal.valueOf(f.getSuggestedQty())),
                f.getExplanation(), f.isExplanationByAi(), f.getComputedAt());
    }
}
