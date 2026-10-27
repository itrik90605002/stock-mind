package com.warehouse.assistant.dto;

/**
 * @param enabled   AI is configured on
 * @param available AI is enabled and not cooling down after a failure
 */
public record AssistantStatus(boolean enabled, boolean available, String model) {
}
