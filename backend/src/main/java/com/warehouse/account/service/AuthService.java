package com.warehouse.account.service;

import com.warehouse.account.domain.User;
import com.warehouse.account.domain.UserRepository;
import com.warehouse.account.dto.AuthResponse;
import com.warehouse.account.dto.ChangePasswordRequest;
import com.warehouse.account.dto.LoginRequest;
import com.warehouse.account.dto.UserResponse;
import com.warehouse.common.exception.NotFoundException;
import com.warehouse.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    /** Hash of a random password, compared against when the email is unknown so timing does not reveal it. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(User.normalizeEmail(request.email())).orElse(null);
        boolean matches = passwordEncoder.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches) {
            throw new BadCredentialsException("Invalid email or password");
        }
        if (!user.isActive()) {
            throw new DisabledException("This account has been deactivated");
        }
        return new AuthResponse(jwtService.issueToken(user), jwtService.expiresAt(), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return userRepository.findWithWarehousesById(userId).map(UserResponse::from)
                .orElseThrow(() -> NotFoundException.of("User", userId));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
    }
}
