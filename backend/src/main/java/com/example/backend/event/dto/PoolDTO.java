package com.example.backend.event.dto;

import com.example.backend.dto.MatchDTO;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class PoolDTO {
    private UUID id;
    private UUID eventId;
    private String name;
    private List<UUID> teamIds;
    private List<MatchDTO> matches;
    private List<PoolStandingDTO> standings;
    private boolean isComplete;
}
