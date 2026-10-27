package com.warehouse.forecast.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockForecastRepository extends JpaRepository<StockForecast, Long> {

    /**
     * Open suggestions in the given warehouses, most urgent (fewest days of cover) first.
     * <p>
     * Forecasts are a snapshot that counts only placed orders as incoming. A suggestion is therefore
     * left out while a purchase order the snapshot did not see covers it: any draft, or any order raised
     * after the forecast ran. Otherwise the item would stay on the list after it was ordered (or even
     * received), inviting a second order. The next forecast run takes those orders into account.
     * {@link StockForecast#NOT_COVERED_SQL} is the same rule in SQL, for the counts and the assistant.
     */
    @EntityGraph(attributePaths = {"product", "product.supplier", "warehouse"})
    @Query("""
            select f from StockForecast f
            where f.warehouse.id in :warehouseIds and f.suggestedQty > 0 and f.product.archived = false
              and not exists (
                select 1 from PurchaseOrderLine l join l.purchaseOrder o
                where l.product = f.product and o.warehouse = f.warehouse
                  and (o.status = com.warehouse.purchasing.domain.PurchaseOrderStatus.DRAFT
                       or (o.status <> com.warehouse.purchasing.domain.PurchaseOrderStatus.CANCELLED
                           and o.createdAt > f.computedAt)))
            order by f.daysOfCover asc nulls last, f.suggestedQty desc
            """)
    List<StockForecast> findSuggestions(@Param("warehouseIds") Collection<Long> warehouseIds);

    @Query("select max(f.computedAt) from StockForecast f")
    Optional<Instant> lastComputedAt();
}
