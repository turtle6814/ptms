package com.example.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class EventDTO {
    private UUID id;
    private UUID tournamentId;
    private String name;
    private String status;
    private String format;
    private List<TeamDTO> teams;
    private List<PoolDTO> pools;
    private EliminationBracketDTO eliminationBracket;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
