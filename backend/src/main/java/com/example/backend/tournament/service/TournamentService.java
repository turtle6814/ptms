package com.example.backend.tournament.service;

import com.example.backend.event.dto.EventDTO;
import com.example.backend.tournament.dto.CreateTournamentRequest;
import com.example.backend.tournament.dto.TournamentDTO;
import com.example.backend.tournament.dto.UpdateTournamentRequest;

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
