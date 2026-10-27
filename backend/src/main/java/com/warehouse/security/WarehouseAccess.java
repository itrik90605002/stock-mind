package com.warehouse.security;

import com.warehouse.warehouse.domain.Warehouse;
import com.warehouse.warehouse.domain.WarehouseRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Enforces warehouse-level authorisation. Role checks ("is a Manager") live on controllers with
 * {@code @PreAuthorize}; this class answers "may this user act on <em>this</em> warehouse".
 */
@Component
public class WarehouseAccess {

    private final WarehouseRepository warehouseRepository;

    public WarehouseAccess(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    /** @throws AccessDeniedException when the user is not assigned to the warehouse */
    public void require(AuthUser user, Long warehouseId) {
        if (!user.canAccess(warehouseId)) {
            throw new AccessDeniedException("You are not assigned to this warehouse");
        }
    }

    /** @throws AccessDeniedException when the user is not assigned to every one of the warehouses */
    public void requireAll(AuthUser user, Collection<Long> warehouseIds) {
        warehouseIds.forEach(id -> require(user, id));
    }

    /**
     * Warehouse ids the user may read, optionally narrowed to one warehouse. Used to scope list
     * queries; an empty result means "nothing visible".
     *
     * @param requested a specific warehouse filter from the request, or {@code null} for all accessible
     */
    public List<Long> scope(AuthUser user, Long requested) {
        if (requested != null) {
            require(user, requested);
            return List.of(requested);
        }
        if (user.isAdmin()) {
            return warehouseRepository.findAll().stream().map(Warehouse::getId).toList();
        }
        return List.copyOf(user.warehouseIds());
    }
}
