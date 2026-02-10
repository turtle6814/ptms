package com.example.backend.service;

import com.example.backend.dto.AuthDtos.*;
import com.example.backend.dto.UserDTO;

public interface AuthService {
    AuthResponse signup(SignupRequest request);

    AuthResponse login(LoginRequest request);

    UserDTO getCurrentUser();
}
