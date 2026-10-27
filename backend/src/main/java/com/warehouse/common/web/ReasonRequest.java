package com.warehouse.common.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for actions that must be justified (deactivation, cancellation ...). */
public record ReasonRequest(@NotBlank @Size(max = 255) String reason) {
}
