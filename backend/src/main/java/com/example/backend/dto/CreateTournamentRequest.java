package com.example.backend.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class CreateTournamentRequest {
    private String name;
    private UUID eventId;
    private List<PoolConfigDTO> pools;
}
