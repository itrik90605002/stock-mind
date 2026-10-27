package com.warehouse.stock.web;

import com.warehouse.common.web.PageResponse;
import com.warehouse.common.web.ReasonRequest;
import com.warehouse.security.AuthUser;
import com.warehouse.stock.domain.MovementType;
import com.warehouse.stock.domain.ReferenceType;
import com.warehouse.stock.dto.MovementResponse;
import com.warehouse.stock.service.StockCommandService;
import com.warehouse.stock.service.StockQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/movements")
@Validated
public class MovementController {

    private final StockQueryService queries;
    private final StockCommandService commands;

    public MovementController(StockQueryService queries, StockCommandService commands) {
        this.queries = queries;
        this.commands = commands;
    }

    @GetMapping
    public PageResponse<MovementResponse> list(@AuthenticationPrincipal AuthUser user,
                                               @RequestParam(required = false) Long warehouseId,
                                               @RequestParam(required = false) Long productId,
                                               @RequestParam(required = false) MovementType type,
                                               @RequestParam(required = false) ReferenceType referenceType,
                                               @RequestParam(required = false) Long referenceId,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                               @RequestParam(defaultValue = "0") @Min(0) int page,
                                               @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return queries.movements(user, warehouseId, productId, type, referenceType, referenceId,
                from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(), page, size);
    }

    @GetMapping("/{id}")
    public MovementResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return queries.movement(user, id);
    }

    @PostMapping("/{id}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public MovementResponse reverse(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                    @Valid @RequestBody ReasonRequest request,
                                    @RequestHeader(value = StockController.IDEMPOTENCY_HEADER, required = false) String key) {
        return commands.reverse(id, request.reason(), user, key);
    }
}
