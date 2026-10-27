package com.warehouse.stock.domain;

import com.warehouse.common.exception.BusinessRuleException;
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
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Current quantity of one product in one warehouse.
 *
 * <ul>
 *   <li>{@code onHand}: physically present; always equals the sum of this pair's ledger entries.</li>
 *   <li>{@code reserved}: part of on-hand promised to draft outgoing transfers.</li>
 *   <li>{@code available} = onHand - reserved: what can be issued or newly reserved.</li>
 * </ul>
 *
 * <p>Concurrent writers are serialised by the {@link Version} column: a second transaction that
 * read the same version fails at commit and is retried by
 * {@link com.warehouse.stock.service.StockCommandExecutor}. The database CHECK constraints are a
 * last line of defence against negative stock.
 *
 * <p>Only {@link com.warehouse.stock.service.StockLedgerService} should call the mutators, so that
 * every on-hand change is paired with a ledger entry.
 */
@Entity
@Table(name = "stock_levels")
public class StockLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Column(name = "on_hand", nullable = false)
    private int onHand;

    @Column(nullable = false)
    private int reserved;

    @Column(name = "reorder_point", nullable = false)
    private int reorderPoint;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockLevel() {
    }

    public int available() {
        return onHand - reserved;
    }

    public boolean isLow() {
        return available() <= reorderPoint;
    }

    /**
     * Changes on-hand by {@code delta}. A decrease may only consume <em>available</em> stock;
     * reserved stock leaves only through {@link #consumeReservation}.
     */
    void changeOnHand(int delta, Instant now) {
        if (delta < 0 && -delta > available()) {
            throw insufficient(-delta);
        }
        this.onHand += delta;
        this.updatedAt = now;
    }

    void reserve(int quantity, Instant now) {
        if (quantity > available()) {
            throw insufficient(quantity);
        }
        this.reserved += quantity;
        this.updatedAt = now;
    }

    void releaseReservation(int quantity, Instant now) {
        if (quantity > reserved) {
            throw new IllegalStateException("Releasing more than is reserved for stock level " + id);
        }
        this.reserved -= quantity;
        this.updatedAt = now;
    }

    void consumeReservation(int quantity, Instant now) {
        if (quantity > reserved) {
            throw new IllegalStateException("Consuming more than is reserved for stock level " + id);
        }
        this.reserved -= quantity;
        this.onHand -= quantity;
        this.updatedAt = now;
    }

    public void updateSettings(int reorderPoint, Integer leadTimeDays, Instant now) {
        this.reorderPoint = reorderPoint;
        this.leadTimeDays = leadTimeDays;
        this.updatedAt = now;
    }

    private BusinessRuleException insufficient(int requested) {
        return new BusinessRuleException("INSUFFICIENT_STOCK", String.format(
                "Not enough stock of %s in %s: requested %d, available %d (on hand %d, reserved %d)",
                product.getSku(), warehouse.getCode(), requested, available(), onHand, reserved));
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public int getOnHand() {
        return onHand;
    }

    public int getReserved() {
        return reserved;
    }

    public int getReorderPoint() {
        return reorderPoint;
    }

    public Integer getLeadTimeDays() {
        return leadTimeDays;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
