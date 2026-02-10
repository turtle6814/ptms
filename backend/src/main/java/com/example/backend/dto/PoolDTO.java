package com.example.backend.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class PoolDTO {
    private UUID id;
    private UUID tournamentId;
    private String name;
    private List<UUID> teamIds;
    private List<MatchDTO> matches;
    private List<PoolStandingDTO> standings;
    private boolean isComplete;
}
