package com.example.backend.event.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class EventRefereeResponse {
    private UUID id;
    private UUID eventId;
    private UUID refereeId;
    private String refereeUsername;
    private LocalDateTime assignedAt;
}
