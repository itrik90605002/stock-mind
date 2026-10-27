package com.warehouse.account.dto;

import com.warehouse.account.domain.Role;
import com.warehouse.account.domain.User;
import com.warehouse.warehouse.dto.WarehouseRef;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record UserResponse(Long id, String email, String fullName, Role role, boolean active,
                           String statusReason, List<WarehouseRef> warehouses, Instant createdAt) {

    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.isActive(),
                u.getStatusReason(),
                u.getWarehouses().stream().map(WarehouseRef::from)
                        .sorted(Comparator.comparing(WarehouseRef::code)).toList(),
                u.getCreatedAt());
    }
}
