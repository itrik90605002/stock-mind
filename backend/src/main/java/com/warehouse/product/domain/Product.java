package com.warehouse.product.domain;

import com.warehouse.supplier.domain.Supplier;
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
import java.util.Objects;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String unit;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    public Product(String sku, String name, String category, String unit, BigDecimal unitCost, String description,
                   Supplier supplier, Instant now) {
        this.sku = normalizeSku(sku);
        this.createdAt = now;
        update(name, category, unit, unitCost, description, supplier, now);
    }

    public static String normalizeSku(String sku) {
        return Objects.requireNonNull(sku).trim().toUpperCase();
    }

    public void update(String name, String category, String unit, BigDecimal unitCost, String description,
                       Supplier supplier, Instant now) {
        this.name = Objects.requireNonNull(name).trim();
        this.category = Objects.requireNonNull(category).trim();
        this.unit = Objects.requireNonNull(unit).trim();
        this.unitCost = Objects.requireNonNull(unitCost);
        this.description = description;
        this.supplier = supplier;
        this.updatedAt = now;
    }

    public void archive(Instant now) {
        this.archived = true;
        this.updatedAt = now;
    }

    public void restore(Instant now) {
        this.archived = false;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public String getDescription() {
        return description;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public boolean isArchived() {
        return archived;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
