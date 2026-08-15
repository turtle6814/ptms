package com.example.backend.event.dto.response;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class EventResponse {
    private UUID id;
    private UUID tournamentId;
    private String name;
    private String status;
    private String format;
    private List<TeamResponse> teams;
    private List<PoolResponse> pools;
    private EliminationBracketResponse eliminationBracket;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
