package com.example.fancontrol.controller;

import com.example.fancontrol.dto.AuthenticatedUserResponse;
import com.example.fancontrol.dto.LoginRequest;
import com.example.fancontrol.dto.LoginResponse;
import com.example.fancontrol.dto.RegisterRequest;
import com.example.fancontrol.dto.UserSummaryResponse;
import com.example.fancontrol.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserSummaryResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public AuthenticatedUserResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        return authService.currentUser(Long.parseLong(jwt.getSubject()));
    }
}
