package com.warehouse.dashboard;

import com.warehouse.security.AuthUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/api/dashboard")
    public DashboardDtos.Dashboard dashboard(@AuthenticationPrincipal AuthUser user,
                                             @RequestParam(required = false) Long warehouseId) {
        return service.dashboard(user, warehouseId);
    }

    @GetMapping("/api/dashboard/counts")
    public DashboardDtos.NavCounts counts(@AuthenticationPrincipal AuthUser user) {
        return service.navCounts(user);
    }

    @GetMapping("/api/reports/valuation")
    public List<DashboardDtos.ValuationRow> valuation(@AuthenticationPrincipal AuthUser user,
                                                      @RequestParam(required = false) Long warehouseId) {
        return service.valuation(user, warehouseId);
    }

    @GetMapping("/api/reports/movers")
    public DashboardDtos.Movers movers(@AuthenticationPrincipal AuthUser user,
                                       @RequestParam(required = false) Long warehouseId,
                                       @RequestParam(defaultValue = "30") @Min(7) @Max(365) int days) {
        return service.movers(user, warehouseId, days);
    }
}
