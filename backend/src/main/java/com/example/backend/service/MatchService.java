package com.example.backend.service;

import com.example.backend.dto.ForfeitRequest;
import com.example.backend.dto.MatchDTO;
import com.example.backend.dto.ScoreRulesDTO;
import com.example.backend.dto.ScoreUpdateRequest;

import java.util.UUID;

public interface MatchService {
    MatchDTO updateScore(UUID matchId, ScoreUpdateRequest request, String username);

    MatchDTO updateRules(UUID matchId, ScoreRulesDTO request, String username);

    MatchDTO recordForfeit(UUID matchId, ForfeitRequest request, String username);
}
