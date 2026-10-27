package com.warehouse.forecast.web;

import com.warehouse.forecast.dto.CreateOrdersFromSuggestionsRequest;
import com.warehouse.forecast.dto.ForecastRunResult;
import com.warehouse.forecast.dto.ReorderSuggestionsResponse;
import com.warehouse.forecast.service.ForecastService;
import com.warehouse.forecast.service.ReorderService;
import com.warehouse.purchasing.dto.CreatedPurchaseOrders;
import com.warehouse.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reorder")
public class ReorderController {

    private final ReorderService reorderService;
    private final ForecastService forecastService;

    public ReorderController(ReorderService reorderService, ForecastService forecastService) {
        this.reorderService = reorderService;
        this.forecastService = forecastService;
    }

    @GetMapping("/suggestions")
    public ReorderSuggestionsResponse suggestions(@AuthenticationPrincipal AuthUser user,
                                                  @RequestParam(required = false) Long warehouseId) {
        return reorderService.suggestions(user, warehouseId);
    }

    @PostMapping("/recompute")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ForecastRunResult recompute() {
        return forecastService.recompute();
    }

    @PostMapping("/purchase-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public CreatedPurchaseOrders createOrders(@AuthenticationPrincipal AuthUser user,
                                              @Valid @RequestBody CreateOrdersFromSuggestionsRequest request,
                                              @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return reorderService.createOrders(request, user, key);
    }
}
