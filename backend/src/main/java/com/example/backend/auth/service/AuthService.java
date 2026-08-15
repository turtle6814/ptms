package com.example.backend.auth.service;

import com.example.backend.auth.dto.response.AuthResponse;
import com.example.backend.auth.dto.request.LoginRequest;
import com.example.backend.auth.dto.request.SignupRequest;
import com.example.backend.user.dto.response.UserResponse;

public interface AuthService {
    AuthResponse signup(SignupRequest request);

    AuthResponse login(LoginRequest request);

    UserResponse getCurrentUser();
}
