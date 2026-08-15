package com.example.backend.user.dto.response;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class UserResponse {
    private UUID id;
    private String username;
    private String phoneNumber;
    private LocalDateTime createdAt;
}
