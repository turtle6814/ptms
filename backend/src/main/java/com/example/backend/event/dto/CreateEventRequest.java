package com.example.backend.event.dto;

import com.example.backend.match.dto.ScoreRulesDTO;
import com.example.backend.enums.EventFormat;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class CreateEventRequest {
    private String name;
    private UUID tournamentId;
    private List<PoolConfigDTO> pools;
    private EventFormat format = EventFormat.POOL_TO_ELIM;
    private ScoreRulesDTO poolStageRules;
    private ScoreRulesDTO playoffStageRules;
}
