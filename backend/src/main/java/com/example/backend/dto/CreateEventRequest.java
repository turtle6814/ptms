package com.example.backend.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class CreateEventRequest {
    private String name;
    private UUID tournamentId;
    private List<PoolConfigDTO> pools;
}
