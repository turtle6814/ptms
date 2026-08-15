package com.example.backend.auth.controller;

import com.example.backend.auth.dto.response.AuthResponse;
import com.example.backend.auth.dto.request.LoginRequest;
import com.example.backend.auth.dto.request.SignupRequest;
import com.example.backend.auth.service.AuthService;
import com.example.backend.dto.ApiResponse;
import com.example.backend.security.RateLimiter;
import com.example.backend.user.dto.response.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RateLimiter rateLimiter;

    @PostMapping(value = {"/signup", "/register"})
    public ResponseEntity<ApiResponse<AuthResponse>> signup(@Valid @RequestBody SignupRequest request,
            HttpServletRequest httpRequest) {
        rateLimiter.allowOrThrow(httpRequest.getRemoteAddr());
        AuthResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping(value = {"/signin", "/login"})
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        rateLimiter.allowOrThrow(httpRequest.getRemoteAddr());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        UserResponse user = authService.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(user));
    }
}
