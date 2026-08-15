package com.example.backend.event.dto.request;

import com.example.backend.match.dto.ScoreRules;
import com.example.backend.enums.EventFormat;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class CreateEventRequest {
    private String name;
    private UUID tournamentId;
    private List<PoolConfigRequest> pools;
    private EventFormat format = EventFormat.POOL_TO_ELIMINATION;
    private int advancementPerPool = 2;
    private int wildcardCount = 0;
    private ScoreRules poolStageRules;
    private ScoreRules playoffStageRules;
}
