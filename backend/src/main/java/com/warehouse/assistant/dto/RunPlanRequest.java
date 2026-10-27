package com.warehouse.assistant.dto;

import com.warehouse.assistant.plan.QueryPlan;
import jakarta.validation.constraints.NotNull;

/** Runs a plan directly (quick-query buttons). It is validated exactly like a model-produced plan. */
public record RunPlanRequest(@NotNull QueryPlan plan) {
}
