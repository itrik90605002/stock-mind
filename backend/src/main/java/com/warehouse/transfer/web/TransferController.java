package com.warehouse.transfer.web;

import com.warehouse.common.web.PageResponse;
import com.warehouse.security.AuthUser;
import com.warehouse.transfer.domain.TransferStatus;
import com.warehouse.transfer.dto.CreateTransferRequest;
import com.warehouse.transfer.dto.ReceiveTransferRequest;
import com.warehouse.transfer.dto.TransferPatchRequest;
import com.warehouse.transfer.dto.TransferResponse;
import com.warehouse.transfer.service.TransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

    @PatchMapping("/{id}")
    public TransferResponse patch(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                  @Valid @RequestBody TransferPatchRequest request,
                                  @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String key) {
        return switch (request.status()) {
            case DISPATCHED -> service.dispatch(id, user, key);
            case CANCELLED -> {
                if (!user.isManagerOrAdmin()) {
                    throw new AccessDeniedException("Only Managers and Admins can cancel a transfer");
                }
                if (request.reason() == null || request.reason().isBlank()) {
                    throw new IllegalArgumentException("A reason is required");
                }
                yield service.cancel(id, request.reason().trim(), user);
            }
        };
    }

    @PostMapping("/{id}/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse receive(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                    @Valid @RequestBody(required = false) ReceiveTransferRequest request,
                                    @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String key) {
        return service.receive(id, request, user, key);
    }
}
