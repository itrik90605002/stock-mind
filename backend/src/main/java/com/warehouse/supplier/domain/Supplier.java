package com.warehouse.supplier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "suppliers")
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "contact_email")
    private String contactEmail;

    private String phone;

    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Supplier() {
    }

    public Supplier(String name, String contactEmail, String phone, int leadTimeDays, Instant createdAt) {
        update(name, contactEmail, phone, leadTimeDays);
        this.createdAt = createdAt;
    }

    public void update(String name, String contactEmail, String phone, int leadTimeDays) {
        this.name = Objects.requireNonNull(name).trim();
        this.contactEmail = contactEmail;
        this.phone = phone;
        this.leadTimeDays = leadTimeDays;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getPhone() {
        return phone;
    }

    public int getLeadTimeDays() {
        return leadTimeDays;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
