package com.example.backend.dto;

import lombok.Data;

public class AuthDtos {
    @Data
    public static class LoginRequest {
        private String phoneNumber;
        private String password;
    }

    @Data
    public static class SignupRequest {
        private String username;
        private String phoneNumber;
        private String password;
    }

    @Data
    public static class AuthResponse {
        private UserDTO user;
        private String token;
    }
}
