package com.warehouse.stock.web;

import com.warehouse.common.web.PageResponse;
import com.warehouse.security.AuthUser;
import com.warehouse.stock.dto.ConsistencyReport;
import com.warehouse.stock.dto.StockRow;
import com.warehouse.stock.dto.StockSettingsRequest;
import com.warehouse.stock.service.ConsistencyService;
import com.warehouse.stock.service.StockCommandService;
import com.warehouse.stock.service.StockQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stock")
@Validated
public class StockController {

    static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final StockQueryService queries;
    private final StockCommandService commands;
    private final ConsistencyService consistency;

    public StockController(StockQueryService queries, StockCommandService commands, ConsistencyService consistency) {
        this.queries = queries;
        this.commands = commands;
        this.consistency = consistency;
    }

    @GetMapping
    public PageResponse<StockRow> list(@AuthenticationPrincipal AuthUser user,
                                       @RequestParam(required = false) Long warehouseId,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(required = false) String category,
                                       @RequestParam(defaultValue = "false") boolean lowOnly,
                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                       @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return queries.stock(user, warehouseId, q, category, lowOnly, page, size);
    }

    @GetMapping("/product/{productId}")
    public List<StockRow> forProduct(@AuthenticationPrincipal AuthUser user, @PathVariable Long productId) {
        return queries.forProduct(user, productId);
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public StockRow updateSettings(@AuthenticationPrincipal AuthUser user,
                                   @Valid @RequestBody StockSettingsRequest request) {
        return commands.updateSettings(request, user);
    }

    @GetMapping("/consistency")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ConsistencyReport consistency(@AuthenticationPrincipal AuthUser user,
                                         @RequestParam(required = false) Long warehouseId) {
        return consistency.check(user, warehouseId);
    }
}
