package com.warehouse.assistant.web;

import com.warehouse.assistant.dto.AskRequest;
import com.warehouse.assistant.dto.AssistantAnswer;
import com.warehouse.assistant.dto.AssistantStatus;
import com.warehouse.assistant.dto.QueryLogResponse;
import com.warehouse.assistant.dto.RunPlanRequest;
import com.warehouse.assistant.service.AssistantService;
import com.warehouse.common.web.PageResponse;
import com.warehouse.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assistant")
@Validated
public class AssistantController {

    private final AssistantService service;

    public AssistantController(AssistantService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public AssistantStatus status() {
        return service.status();
    }

    @PostMapping("/ask")
    public AssistantAnswer ask(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody AskRequest request) {
        return service.ask(request.question(), user);
    }

    @PostMapping("/run")
    public AssistantAnswer run(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody RunPlanRequest request) {
        return service.runPlan(request.plan(), user);
    }

    @GetMapping("/history")
    public List<QueryLogResponse> history(@AuthenticationPrincipal AuthUser user) {
        return service.history(user);
    }

    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<QueryLogResponse> logs(@RequestParam(defaultValue = "0") @Min(0) int page,
                                               @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return service.allLogs(page, size);
    }
}
