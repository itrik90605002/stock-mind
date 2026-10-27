package com.warehouse.purchasing.web;

import com.warehouse.common.web.PageResponse;
import com.warehouse.common.web.ReasonRequest;
import com.warehouse.purchasing.domain.PurchaseOrderStatus;
import com.warehouse.purchasing.dto.PurchaseOrderRequest;
import com.warehouse.purchasing.dto.PurchaseOrderResponse;
import com.warehouse.purchasing.dto.ReceivePurchaseOrderRequest;
import com.warehouse.purchasing.service.PurchaseOrderService;
import com.warehouse.security.AuthUser;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchase-orders")
@Validated
public class PurchaseOrderController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final PurchaseOrderService service;

    public PurchaseOrderController(PurchaseOrderService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<PurchaseOrderResponse> list(@AuthenticationPrincipal AuthUser user,
                                                    @RequestParam(required = false) PurchaseOrderStatus status,
                                                    @RequestParam(defaultValue = "false") boolean open,
                                                    @RequestParam(required = false) Long warehouseId,
                                                    @RequestParam(required = false) Long supplierId,
                                                    @RequestParam(defaultValue = "0") @Min(0) int page,
                                                    @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return service.list(user, status, open, warehouseId, supplierId, page, size);
    }

    @GetMapping("/{id}")
    public PurchaseOrderResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return service.get(user, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public PurchaseOrderResponse create(@AuthenticationPrincipal AuthUser user,
                                        @Valid @RequestBody PurchaseOrderRequest request,
                                        @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String key) {
        return service.create(request, user, key);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public PurchaseOrderResponse revise(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                        @Valid @RequestBody PurchaseOrderRequest request) {
        return service.revise(id, request, user);
    }

    @PostMapping("/{id}/place")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public PurchaseOrderResponse place(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return service.place(id, user);
    }

    @PostMapping("/{id}/receive")
    public PurchaseOrderResponse receive(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                         @Valid @RequestBody ReceivePurchaseOrderRequest request,
                                         @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String key) {
        return service.receive(id, request, user, key);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public PurchaseOrderResponse cancel(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                        @Valid @RequestBody ReasonRequest request) {
        return service.cancel(id, request.reason(), user);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public PurchaseOrderResponse closeShort(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                            @Valid @RequestBody ReasonRequest request) {
        return service.closeShort(id, request.reason(), user);
    }
}
