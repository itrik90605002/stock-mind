package com.warehouse.transfer.domain;

import com.warehouse.account.domain.User;
import com.warehouse.common.exception.BusinessRuleException;
import com.warehouse.product.domain.Product;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_warehouse_id")
    private Warehouse source;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_warehouse_id")
    private Warehouse destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    private String note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispatched_by")
    private User dispatchedBy;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private User receivedBy;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    @BatchSize(size = 50) // list pages load all lines in one query instead of one per transfer
    private List<TransferLine> lines = new ArrayList<>();

    protected Transfer() {
    }

    public Transfer(Warehouse source, Warehouse destination, String note, User createdBy, Instant now) {
        if (source.getId().equals(destination.getId())) {
            throw new BusinessRuleException("SAME_WAREHOUSE", "Source and destination must be different warehouses");
        }
        this.source = source;
        this.destination = destination;
        this.note = note;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.status = TransferStatus.DRAFT;
    }

    public void addLine(Product product, int quantity) {
        requireStatus(TransferStatus.DRAFT, "add lines to");
        lines.add(new TransferLine(this, product, quantity));
    }

    public void markDispatched(User user, Instant now) {
        requireStatus(TransferStatus.DRAFT, "dispatch");
        this.status = TransferStatus.DISPATCHED;
        this.dispatchedBy = user;
        this.dispatchedAt = now;
    }

    public void markReceived(Map<Long, Integer> receivedByLineId, User user, Instant now) {
        requireStatus(TransferStatus.DISPATCHED, "receive");
        for (Long lineId : receivedByLineId.keySet()) {
            if (lines.stream().noneMatch(l -> l.getId().equals(lineId))) {
                throw new IllegalArgumentException("Line " + lineId + " does not belong to transfer " + id);
            }
        }
        lines.forEach(line -> line.markReceived(receivedByLineId.getOrDefault(line.getId(), line.getQuantity())));
        this.status = TransferStatus.RECEIVED;
        this.receivedBy = user;
        this.receivedAt = now;
    }

    public void cancel(String reason, Instant now) {
        requireStatus(TransferStatus.DRAFT, "cancel");
        this.status = TransferStatus.CANCELLED;
        this.cancelledAt = now;
        String cancelNote = "Cancelled: " + reason;
        this.note = note == null || note.isBlank() ? cancelNote : note + " | " + cancelNote;
    }

    private void requireStatus(TransferStatus expected, String action) {
        if (status != expected) {
            throw new BusinessRuleException("INVALID_TRANSFER_STATUS",
                    "Cannot " + action + " transfer #" + id + " because it is " + status);
        }
    }

    public int totalUnits() {
        return lines.stream().mapToInt(TransferLine::getQuantity).sum();
    }

    public Long getId() {
        return id;
    }

    public Warehouse getSource() {
        return source;
    }

    public Warehouse getDestination() {
        return destination;
    }

    public TransferStatus getStatus() {
        return status;
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

    public User getDispatchedBy() {
        return dispatchedBy;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public User getReceivedBy() {
        return receivedBy;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public List<TransferLine> getLines() {
        return lines;
    }
}
