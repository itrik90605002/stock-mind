package com.warehouse.security;

import com.warehouse.account.domain.Role;

import java.util.Set;

public record AuthUser(Long id, String email, String fullName, Role role, Set<Long> warehouseIds) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isManagerOrAdmin() {
        return role == Role.ADMIN || role == Role.MANAGER;
    }

    public boolean canAccess(Long warehouseId) {
        return isAdmin() || warehouseIds.contains(warehouseId);
    }
}
