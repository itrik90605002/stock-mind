package com.warehouse.account.dto;

import java.time.Instant;

public record AuthResponse(String token, Instant expiresAt, UserResponse user) {
}
