package com.example.backend.event.dto.request;

import com.example.backend.match.dto.ScoreRules;
import com.example.backend.enums.EventFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class CreateEventRequest {
    @NotBlank(message = "Event name is required")
    @Size(max = 255, message = "Event name must be at most 255 characters")
    private String name;
    private UUID tournamentId;
    @Valid
    private List<PoolConfigRequest> pools;
    private EventFormat format = EventFormat.POOL_TO_ELIMINATION;
    private int advancementPerPool = 2;
    private int wildcardCount = 0;
    private ScoreRules poolStageRules;
    private ScoreRules playoffStageRules;
}
