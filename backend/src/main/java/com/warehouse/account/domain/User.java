package com.warehouse.account.domain;

import com.warehouse.warehouse.domain.Warehouse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A person using the system. Managers and Clerks act only on the warehouses assigned to them;
 * Admins implicitly see every warehouse.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean active;

    /** Why the account was deactivated, shown to admins. */
    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToMany
    @JoinTable(name = "user_warehouses",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "warehouse_id"))
    private Set<Warehouse> warehouses = new HashSet<>();

    protected User() {
        // for JPA
    }

    public User(String email, String passwordHash, String fullName, Role role, Instant createdAt) {
        this.email = normalizeEmail(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.fullName = Objects.requireNonNull(fullName);
        this.role = Objects.requireNonNull(role);
        this.active = true;
        this.createdAt = createdAt;
    }

    public static String normalizeEmail(String email) {
        return Objects.requireNonNull(email).trim().toLowerCase();
    }

    public void update(String fullName, Role role) {
        this.fullName = Objects.requireNonNull(fullName);
        this.role = Objects.requireNonNull(role);
    }

    public void assignWarehouses(Collection<Warehouse> assigned) {
        this.warehouses.clear();
        this.warehouses.addAll(assigned);
    }

    public void deactivate(String reason) {
        this.active = false;
        this.statusReason = reason;
    }

    public void activate() {
        this.active = true;
        this.statusReason = null;
    }

    public void changePassword(String newHash) {
        this.passwordHash = Objects.requireNonNull(newHash);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<Warehouse> getWarehouses() {
        return warehouses;
    }
}
