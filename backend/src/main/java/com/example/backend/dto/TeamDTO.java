package com.example.backend.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class TeamDTO {
    private UUID id;
    private String name;
    private UUID tournamentId;
    private UUID poolId;
}
