package com.warehouse.assistant.dto;

import com.warehouse.assistant.plan.QueryPlan;
import jakarta.validation.constraints.NotNull;

public record RunPlanRequest(@NotNull QueryPlan plan) {
}
