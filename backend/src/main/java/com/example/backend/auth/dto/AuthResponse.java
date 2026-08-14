package com.example.backend.auth.dto;

import com.example.backend.user.dto.UserDTO;
import lombok.Data;

@Data
public class AuthResponse {
    private UserDTO user;
    private String token;
}
