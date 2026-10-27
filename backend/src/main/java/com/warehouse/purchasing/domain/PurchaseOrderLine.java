package com.warehouse.purchasing.domain;

import com.warehouse.product.domain.Product;
import com.warehouse.common.exception.BusinessRuleException;
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

@Entity
@Table(name = "purchase_order_lines")
public class PurchaseOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "received_quantity", nullable = false)
    private int receivedQuantity;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;

    protected PurchaseOrderLine() {
    }

    PurchaseOrderLine(PurchaseOrder purchaseOrder, Product product, int quantity, BigDecimal unitCost) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.purchaseOrder = purchaseOrder;
        this.product = product;
        this.quantity = quantity;
        this.unitCost = unitCost;
    }

    void receive(int units) {
        if (units <= 0 || units > remaining()) {
            throw new BusinessRuleException("OVER_RECEIPT", "Can receive between 1 and " + remaining() + " of " + product.getSku());
        }
        this.receivedQuantity += units;
    }

    public int remaining() {
        return quantity - receivedQuantity;
    }

    public BigDecimal lineTotal() {
        return unitCost.multiply(BigDecimal.valueOf(quantity));
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

    public int getReceivedQuantity() {
        return receivedQuantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }
}
