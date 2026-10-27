package com.warehouse.purchasing.domain;

import com.warehouse.account.domain.User;
import com.warehouse.common.exception.BusinessRuleException;
import com.warehouse.product.domain.Product;
import com.warehouse.supplier.domain.Supplier;
import com.warehouse.warehouse.domain.Warehouse;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An order placed with a supplier for delivery to one warehouse. The entity enforces the state
 * machine and receipt limits; receipts book stock through the ledger in
 * {@link com.warehouse.purchasing.service.PurchaseOrderService}.
 */
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseOrderStatus status;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    private String note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "ordered_at")
    private Instant orderedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    @BatchSize(size = 50)
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    protected PurchaseOrder() {
        // for JPA
    }

    public PurchaseOrder(Supplier supplier, Warehouse warehouse, LocalDate expectedDate, String note, User createdBy,
                         Instant now) {
        this.supplier = supplier;
        this.warehouse = warehouse;
        this.expectedDate = expectedDate;
        this.note = note;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.status = PurchaseOrderStatus.DRAFT;
    }

    public void addLine(Product product, int quantity, BigDecimal unitCost) {
        requireStatus("edit", PurchaseOrderStatus.DRAFT);
        if (lines.stream().anyMatch(l -> l.getProduct().getId().equals(product.getId()))) {
            throw new IllegalArgumentException("Each product may appear only once per purchase order");
        }
        lines.add(new PurchaseOrderLine(this, product, quantity, unitCost));
    }

    /** Replaces header and lines of a draft. */
    public void reviseDraft(Supplier supplier, Warehouse warehouse, LocalDate expectedDate, String note) {
        requireStatus("edit", PurchaseOrderStatus.DRAFT);
        this.supplier = supplier;
        this.warehouse = warehouse;
        this.expectedDate = expectedDate;
        this.note = note;
        this.lines.clear();
    }

    public void markOrdered(LocalDate defaultExpectedDate, Instant now) {
        requireStatus("place", PurchaseOrderStatus.DRAFT);
        if (lines.isEmpty()) {
            throw new BusinessRuleException("EMPTY_ORDER", "Add at least one line before placing the order");
        }
        this.status = PurchaseOrderStatus.ORDERED;
        this.orderedAt = now;
        if (expectedDate == null) {
            this.expectedDate = defaultExpectedDate;
        }
    }

    /**
     * Books a delivery against the order's lines.
     *
     * @param unitsByLineId units received per line id (only lines with deliveries)
     */
    public void receive(Map<Long, Integer> unitsByLineId, Instant now) {
        requireStatus("receive", PurchaseOrderStatus.ORDERED, PurchaseOrderStatus.PARTIALLY_RECEIVED);
        if (unitsByLineId.isEmpty()) {
            throw new IllegalArgumentException("Enter a received quantity for at least one line");
        }
        unitsByLineId.forEach((lineId, units) -> line(lineId).receive(units));
        boolean complete = lines.stream().allMatch(l -> l.remaining() == 0);
        this.status = complete ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED;
        if (complete) {
            this.closedAt = now;
        }
    }

    public void cancel(String reason, Instant now) {
        requireStatus("cancel", PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.ORDERED);
        this.status = PurchaseOrderStatus.CANCELLED;
        this.closedAt = now;
        appendNote("Cancelled: " + reason);
    }

    /** Accepts a partial delivery as final; remaining quantities are no longer expected. */
    public void closeShort(String reason, Instant now) {
        requireStatus("close", PurchaseOrderStatus.PARTIALLY_RECEIVED);
        this.status = PurchaseOrderStatus.RECEIVED;
        this.closedAt = now;
        appendNote("Closed short: " + reason);
    }

    public PurchaseOrderLine line(Long lineId) {
        return lines.stream().filter(l -> l.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Line " + lineId + " does not belong to order " + id));
    }

    private void appendNote(String text) {
        this.note = note == null || note.isBlank() ? text : note + " | " + text;
    }

    private void requireStatus(String action, PurchaseOrderStatus... allowed) {
        for (PurchaseOrderStatus s : allowed) {
            if (status == s) {
                return;
            }
        }
        throw new BusinessRuleException("INVALID_PO_STATUS",
                "Cannot " + action + " purchase order #" + id + " because it is " + status);
    }

    public BigDecimal totalValue() {
        return lines.stream().map(PurchaseOrderLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int totalUnits() {
        return lines.stream().mapToInt(PurchaseOrderLine::getQuantity).sum();
    }

    /** Units still expected; zero once the order is closed (received in full, closed short or cancelled). */
    public int remainingUnits() {
        return PurchaseOrderStatus.OPEN_FOR_RECEIPT.contains(status)
                ? lines.stream().mapToInt(PurchaseOrderLine::remaining).sum() : 0;
    }

    public Long getId() {
        return id;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public String getNote() {
        return note;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getOrderedAt() {
        return orderedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public List<PurchaseOrderLine> getLines() {
        return lines;
    }
}
