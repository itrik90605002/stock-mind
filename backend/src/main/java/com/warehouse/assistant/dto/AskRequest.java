package com.warehouse.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskRequest(@NotBlank @Size(max = 300) String question) {
}
