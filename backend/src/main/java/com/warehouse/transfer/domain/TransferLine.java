package com.warehouse.transfer.domain;

import com.warehouse.product.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One product on a transfer. {@code receivedQuantity} is set when the destination books the goods in. */
@Entity
@Table(name = "transfer_lines")
public class TransferLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id")
    private Transfer transfer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "received_quantity")
    private Integer receivedQuantity;

    protected TransferLine() {
        // for JPA
    }

    TransferLine(Transfer transfer, Product product, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.transfer = transfer;
        this.product = product;
        this.quantity = quantity;
    }

    void markReceived(int received) {
        if (received < 0 || received > quantity) {
            throw new IllegalArgumentException("Received quantity for " + product.getSku()
                    + " must be between 0 and " + quantity);
        }
        this.receivedQuantity = received;
    }

    /** Units dispatched but not received (lost or damaged in transit); 0 until received. */
    public int shortfall() {
        return receivedQuantity == null ? 0 : quantity - receivedQuantity;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public Integer getReceivedQuantity() {
        return receivedQuantity;
    }
}
