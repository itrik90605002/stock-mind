package com.warehouse.forecast.domain;

import com.warehouse.product.domain.Product;
import com.warehouse.warehouse.domain.Warehouse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "stock_forecasts")
public class StockForecast {

    /**
     * SQL condition (table alias {@code f}) that leaves out suggestions already covered by a purchase order
     * the forecast did not see: any draft, or any order raised after the forecast ran. Mirrors
     * {@link StockForecastRepository#findSuggestions}; used by the dashboard counts and the assistant.
     */
    public static final String NOT_COVERED_SQL = """
            NOT EXISTS (SELECT 1 FROM purchase_order_lines l JOIN purchase_orders o ON o.id = l.purchase_order_id
                        WHERE l.product_id = f.product_id AND o.warehouse_id = f.warehouse_id
                          AND (o.status = 'DRAFT' OR (o.status <> 'CANCELLED' AND o.created_at > f.computed_at)))
            """;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Column(name = "avg_daily_usage", nullable = false)
    private BigDecimal avgDailyUsage;

    @Column(name = "usage_stddev", nullable = false)
    private BigDecimal usageStddev;

    @Column(name = "trend_pct")
    private BigDecimal trendPct;

    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays;

    @Column(name = "safety_stock", nullable = false)
    private int safetyStock;

    @Column(nullable = false)
    private int available;

    @Column(nullable = false)
    private int incoming;

    @Column(name = "days_of_cover")
    private BigDecimal daysOfCover;

    @Column(name = "suggested_qty", nullable = false)
    private int suggestedQty;

    private String explanation;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public BigDecimal getAvgDailyUsage() {
        return avgDailyUsage;
    }

    public BigDecimal getUsageStddev() {
        return usageStddev;
    }

    public BigDecimal getTrendPct() {
        return trendPct;
    }

    public int getLeadTimeDays() {
        return leadTimeDays;
    }

    public int getSafetyStock() {
        return safetyStock;
    }

    public int getAvailable() {
        return available;
    }

    public int getIncoming() {
        return incoming;
    }

    public BigDecimal getDaysOfCover() {
        return daysOfCover;
    }

    public int getSuggestedQty() {
        return suggestedQty;
    }

    public String getExplanation() {
        return explanation;
    }

    public Instant getComputedAt() {
        return computedAt;
    }
}
