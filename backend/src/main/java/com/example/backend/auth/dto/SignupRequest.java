package com.example.backend.auth.dto;

import lombok.Data;

@Data
public class SignupRequest {
    private String username;
    private String phoneNumber;
    private String password;
}
