package com.warehouse.security;

import com.warehouse.account.domain.User;
import com.warehouse.account.domain.UserRepository;
import com.warehouse.warehouse.domain.Warehouse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Authenticates requests carrying {@code Authorization: Bearer <jwt>}.
 *
 * <p>The user and their warehouse assignments are re-loaded on every request (one indexed query)
 * so that deactivation, role changes and warehouse re-assignment take effect immediately rather
 * than when the token expires.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            jwtService.parseUserId(header.substring(BEARER_PREFIX.length()))
                    .flatMap(userRepository::findWithWarehousesById)
                    .filter(User::isActive)
                    .ifPresent(user -> {
                        AuthUser principal = toPrincipal(user);
                        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }
        chain.doFilter(request, response);
    }

    static AuthUser toPrincipal(User user) {
        return new AuthUser(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getWarehouses().stream().map(Warehouse::getId).collect(Collectors.toUnmodifiableSet()));
    }
}
