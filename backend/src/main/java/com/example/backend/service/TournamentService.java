package com.example.backend.service;

import com.example.backend.dto.*;

import java.util.List;
import java.util.UUID;

public interface TournamentService {
    List<TournamentDTO> getAllTournaments(String username);

    TournamentDTO getTournamentById(UUID id);

    TournamentDTO createTournament(CreateTournamentRequest request, String username);

    TournamentDTO updateTournament(UUID id, UpdateTournamentRequest request, String username);

    void deleteTournament(UUID id, String username);

    List<EventDTO> getEvents(UUID tournamentId);
}
