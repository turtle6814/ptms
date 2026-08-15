package com.example.backend.event.dto.response;

import com.example.backend.match.dto.response.MatchResponse;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class PoolResponse {
    private UUID id;
    private UUID eventId;
    private String name;
    private List<UUID> teamIds;
    private List<MatchResponse> matches;
    private List<PoolStandingResponse> standings;
    private boolean isComplete;
}
