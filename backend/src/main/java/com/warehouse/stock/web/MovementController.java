package com.warehouse.stock.web;

import com.warehouse.common.web.PageResponse;
import com.warehouse.security.AuthUser;
import com.warehouse.stock.domain.MovementType;
import com.warehouse.stock.domain.ReferenceType;
import com.warehouse.stock.dto.AdjustRequest;
import com.warehouse.stock.dto.IssueRequest;
import com.warehouse.stock.dto.MovementRequest;
import com.warehouse.stock.dto.MovementResponse;
import com.warehouse.stock.service.StockCommandService;
import com.warehouse.stock.service.StockQueryService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
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
import java.util.Set;

@RestController
@RequestMapping("/api/movements")
@Validated
public class MovementController {

    private final StockQueryService queries;
    private final StockCommandService commands;
    private final Validator validator;

    public MovementController(StockQueryService queries, StockCommandService commands, Validator validator) {
        this.queries = queries;
        this.commands = commands;
        this.validator = validator;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MovementResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody MovementRequest request,
                                   @RequestHeader(value = StockController.IDEMPOTENCY_HEADER, required = false) String key) {
        if (request.reversesId() != null) {
            if (!user.isManagerOrAdmin()) {
                throw new AccessDeniedException("Only Managers and Admins can reverse a movement");
            }
            return commands.reverse(request.reversesId(), requireReason(request.reason()), user, key);
        }
        if (request.type() == null || request.quantity() == null) {
            throw new IllegalArgumentException("Send type (ISSUE or ADJUSTMENT) and quantity, or reversesId");
        }
        return switch (request.type()) {
            case ISSUE -> commands.issue(valid(new IssueRequest(request.productId(), request.warehouseId(),
                    request.quantity(), request.reason())), user, key);
            case ADJUSTMENT -> commands.adjust(valid(new AdjustRequest(request.productId(), request.warehouseId(),
                    request.quantity(), request.reason())), user, key);
            default -> throw new IllegalArgumentException("type must be ISSUE or ADJUSTMENT");
        };
    }

    private <T> T valid(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return request;
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required");
        }
        return reason.trim();
    }
}
