package com.example.backend.service;

import com.example.backend.dto.MatchDTO;
import com.example.backend.dto.ScoreUpdateRequest;

import java.util.UUID;

public interface MatchService {
    MatchDTO updateScore(UUID matchId, ScoreUpdateRequest request);
}
