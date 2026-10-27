package com.warehouse.account.web;

import com.warehouse.account.dto.CreateUserRequest;
import com.warehouse.account.dto.UpdateUserRequest;
import com.warehouse.account.dto.UserResponse;
import com.warehouse.account.service.UserAdminService;
import com.warehouse.common.web.ReasonRequest;
import com.warehouse.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserAdminService service;

    public UserController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request,
                               @AuthenticationPrincipal AuthUser admin) {
        return service.update(id, request, admin.id());
    }

    @PostMapping("/{id}/deactivate")
    public UserResponse deactivate(@PathVariable Long id, @Valid @RequestBody ReasonRequest request,
                                   @AuthenticationPrincipal AuthUser admin) {
        return service.deactivate(id, request.reason(), admin.id());
    }

    @PostMapping("/{id}/activate")
    public UserResponse activate(@PathVariable Long id) {
        return service.activate(id);
    }
}
