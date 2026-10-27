package com.warehouse.warehouse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/** A physical location that holds stock. Identified to users by its short {@code code}. */
@Entity
@Table(name = "warehouses")
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String address;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Warehouse() {
        // for JPA
    }

    public Warehouse(String code, String name, String address, Instant createdAt) {
        this.code = normalizeCode(code);
        this.name = Objects.requireNonNull(name);
        this.address = address;
        this.active = true;
        this.createdAt = createdAt;
    }

    public static String normalizeCode(String code) {
        return Objects.requireNonNull(code).trim().toUpperCase();
    }

    public void update(String name, String address) {
        this.name = Objects.requireNonNull(name);
        this.address = address;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
