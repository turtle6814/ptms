package com.example.backend.tournament.service;

import com.example.backend.event.dto.response.EventResponse;
import com.example.backend.tournament.dto.request.CreateTournamentRequest;
import com.example.backend.tournament.dto.response.TournamentResponse;
import com.example.backend.tournament.dto.request.UpdateTournamentRequest;

import java.util.List;
import java.util.UUID;

public interface TournamentService {
    List<TournamentResponse> getAllTournaments(String username);

    TournamentResponse getTournamentById(UUID id);

    TournamentResponse createTournament(CreateTournamentRequest request, String username);

    TournamentResponse updateTournament(UUID id, UpdateTournamentRequest request, String username);

    void deleteTournament(UUID id, String username);

    List<EventResponse> getEvents(UUID tournamentId);
}
