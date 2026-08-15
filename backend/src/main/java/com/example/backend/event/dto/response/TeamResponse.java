package com.example.backend.event.dto.response;

import lombok.Data;
import java.util.UUID;

@Data
public class TeamResponse {
    private UUID id;
    private String name;
    private UUID eventId;
}
