package com.warehouse.account.web;

import com.warehouse.account.dto.AuthResponse;
import com.warehouse.account.dto.ChangePasswordRequest;
import com.warehouse.account.dto.LoginRequest;
import com.warehouse.account.dto.UserResponse;
import com.warehouse.account.service.AuthService;
import com.warehouse.common.ratelimit.RateLimiter;
import com.warehouse.config.AppProperties;
import com.warehouse.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimiter rateLimiter;
    private final int loginAttemptsPerMinute;

    public AuthController(AuthService authService, RateLimiter rateLimiter, AppProperties properties) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.loginAttemptsPerMinute = properties.security().loginAttemptsPerMinute();
    }

    /** Rate limited per client IP to slow down password guessing. */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        rateLimiter.check("login:" + http.getRemoteAddr(), loginAttemptsPerMinute,
                "Too many login attempts. Please wait a minute and try again.");
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser user) {
        return authService.me(user.id());
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthUser user,
                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user.id(), request);
    }
}
