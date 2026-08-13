package com.example.backend.auth.service;

import com.example.backend.auth.dto.AuthDtos.*;
import com.example.backend.user.dto.UserDTO;

public interface AuthService {
    AuthResponse signup(SignupRequest request);

    AuthResponse login(LoginRequest request);

    UserDTO getCurrentUser();
}
