package com.example.backend.event.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AssignRefereeRequest {
    @NotNull(message = "User id is required")
    private UUID userId;
}
