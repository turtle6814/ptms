package com.example.backend.service;

import com.example.backend.dto.CreateTournamentRequest;
import com.example.backend.dto.TournamentDTO;

import java.util.List;
import java.util.UUID;

public interface TournamentService {
    List<TournamentDTO> getAllTournaments();

    TournamentDTO getTournamentById(UUID id);

    TournamentDTO createTournament(CreateTournamentRequest request);

    void deleteTournament(UUID id);
}
