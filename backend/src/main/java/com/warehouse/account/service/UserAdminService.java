package com.warehouse.account.service;

import com.warehouse.account.domain.Role;
import com.warehouse.account.domain.User;
import com.warehouse.account.domain.UserRepository;
import com.warehouse.account.dto.CreateUserRequest;
import com.warehouse.account.dto.UpdateUserRequest;
import com.warehouse.account.dto.UserResponse;
import com.warehouse.common.exception.BusinessRuleException;
import com.warehouse.common.exception.NotFoundException;
import com.warehouse.warehouse.domain.Warehouse;
import com.warehouse.warehouse.domain.WarehouseRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Set;

/** Admin-only user management. */
@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserAdminService(UserRepository userRepository, WarehouseRepository warehouseRepository,
                            PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.warehouseRepository = warehouseRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAllByOrderByRoleAscFullNameAsc().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = User.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("EMAIL_TAKEN", "A user with this email already exists");
        }
        User user = new User(email, passwordEncoder.encode(request.password()), request.fullName().trim(),
                request.role(), clock.instant());
        user.assignWarehouses(resolveWarehouses(request.role(), request.warehouseIds()));
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request, Long actingAdminId) {
        User user = find(id);
        if (id.equals(actingAdminId) && request.role() != Role.ADMIN) {
            throw new BusinessRuleException("SELF_DEMOTION", "You cannot remove your own admin role");
        }
        user.update(request.fullName().trim(), request.role());
        user.assignWarehouses(resolveWarehouses(request.role(), request.warehouseIds()));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse deactivate(Long id, String reason, Long actingAdminId) {
        if (id.equals(actingAdminId)) {
            throw new BusinessRuleException("SELF_DEACTIVATION", "You cannot deactivate your own account");
        }
        User user = find(id);
        user.deactivate(reason);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse activate(Long id) {
        User user = find(id);
        user.activate();
        return UserResponse.from(user);
    }

    private User find(Long id) {
        return userRepository.findWithWarehousesById(id).orElseThrow(() -> NotFoundException.of("User", id));
    }

    /** Managers and Clerks need at least one warehouse; Admins see all, so assignments are cleared. */
    private List<Warehouse> resolveWarehouses(Role role, Set<Long> ids) {
        if (role == Role.ADMIN) {
            return List.of();
        }
        if (ids.isEmpty()) {
            throw new BusinessRuleException("WAREHOUSE_REQUIRED", "Assign at least one warehouse");
        }
        List<Warehouse> warehouses = warehouseRepository.findAllById(ids);
        if (warehouses.size() != ids.size()) {
            throw new NotFoundException("One or more warehouses do not exist");
        }
        return warehouses;
    }
}
