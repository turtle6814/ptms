package com.example.backend.match.dto.request;

import com.example.backend.enums.MatchStatus;
import lombok.Data;

import java.util.UUID;

@Data
public class ForfeitRequest {
    private UUID winnerId;
    private MatchStatus status;
}
