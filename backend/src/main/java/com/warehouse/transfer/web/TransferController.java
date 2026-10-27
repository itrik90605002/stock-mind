package com.warehouse.transfer.web;

import com.warehouse.common.web.PageResponse;
import com.warehouse.common.web.ReasonRequest;
import com.warehouse.security.AuthUser;
import com.warehouse.transfer.domain.TransferStatus;
import com.warehouse.transfer.dto.CreateTransferRequest;
import com.warehouse.transfer.dto.ReceiveTransferRequest;
import com.warehouse.transfer.dto.TransferResponse;
import com.warehouse.transfer.service.TransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transfers between warehouses. Managers create and cancel; Clerks and Managers dispatch (at the
 * source) and receive (at the destination).
 */
@RestController
@RequestMapping("/api/transfers")
@Validated
public class TransferController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final TransferService service;

    public TransferController(TransferService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<TransferResponse> list(@AuthenticationPrincipal AuthUser user,
                                               @RequestParam(required = false) TransferStatus status,
                                               @RequestParam(required = false) Long warehouseId,
                                               @RequestParam(defaultValue = "0") @Min(0) int page,
                                               @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return service.list(user, status, warehouseId, page, size);
    }

    @GetMapping("/{id}")
    public TransferResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return service.get(user, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public TransferResponse create(@AuthenticationPrincipal AuthUser user,
                                   @Valid @RequestBody CreateTransferRequest request,
                                   @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String key) {
        return service.create(request, user, key);
    }

    @PostMapping("/{id}/dispatch")
    public TransferResponse dispatch(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                     @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String key) {
        return service.dispatch(id, user, key);
    }

    @PostMapping("/{id}/receive")
    public TransferResponse receive(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                    @Valid @RequestBody(required = false) ReceiveTransferRequest request,
                                    @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String key) {
        return service.receive(id, request, user, key);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public TransferResponse cancel(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                   @Valid @RequestBody ReasonRequest request) {
        return service.cancel(id, request.reason(), user);
    }
}
