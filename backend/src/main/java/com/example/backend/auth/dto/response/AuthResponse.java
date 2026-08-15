package com.example.backend.auth.dto.response;

import com.example.backend.user.dto.response.UserResponse;
import lombok.Data;

@Data
public class AuthResponse {
    private UserResponse user;
    private String token;
}
