package com.example.fancontrol.service;

import com.example.fancontrol.dto.AuthenticatedUserResponse;
import com.example.fancontrol.dto.LoginRequest;
import com.example.fancontrol.dto.LoginResponse;
import com.example.fancontrol.dto.RegisterRequest;
import com.example.fancontrol.dto.UserSummaryResponse;
import com.example.fancontrol.exception.ApiException;
import com.example.fancontrol.model.OperatorUser;
import com.example.fancontrol.model.Role;
import com.example.fancontrol.repository.OperatorUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
public class AuthService {

    private final OperatorUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            OperatorUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public UserSummaryResponse register(RegisterRequest request) {
        String name = validateName(request == null ? null : request.name());
        String password = validatePassword(request == null ? null : request.password());

        if (userRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("An account with this name already exists.");
        }

        OperatorUser user = new OperatorUser();
        user.setName(name);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(Role.USER);
        OperatorUser saved = userRepository.saveAndFlush(user);
        return new UserSummaryResponse(saved.getId(), saved.getName());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String name = request == null || request.name() == null ? "" : request.name().trim();
        String password = request == null ? null : request.password();

        OperatorUser user = userRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> ApiException.unauthorized("Invalid name or password."));

        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid name or password.");
        }

        return new LoginResponse(
                jwtTokenService.issueToken(user),
                "Bearer",
                jwtTokenService.getExpirationSeconds(),
                new AuthenticatedUserResponse(user.getId(), user.getName(), user.getRole())
        );
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserResponse currentUser(long id) {
        OperatorUser user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.unauthorized("The account associated with this token no longer exists."));
        return new AuthenticatedUserResponse(user.getId(), user.getName(), user.getRole());
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("Name is required.");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 100) {
            throw ApiException.badRequest("Name must be 100 characters or fewer.");
        }
        return trimmed;
    }

    private String validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw ApiException.badRequest("Password must contain at least 8 characters.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("Password must be no more than 72 bytes.");
        }
        return password;
    }
}
