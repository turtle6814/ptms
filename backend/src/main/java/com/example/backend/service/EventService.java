package com.example.backend.service;

import com.example.backend.dto.*;

import java.util.List;
import java.util.UUID;

public interface EventService {
    List<EventDTO> getAllEvents(String username);

    EventDTO getEventById(UUID id);

    EventDTO createEvent(CreateEventRequest request, String username);

    EventDTO updateEvent(UUID id, UpdateEventRequest request, String username);

    void deleteEvent(UUID id, String username);

    List<TournamentDTO> getTournaments(UUID eventId);

    EventDTO addTournamentToEvent(UUID eventId, UUID tournamentId, String username);

    EventDTO removeTournamentFromEvent(UUID eventId, UUID tournamentId, String username);
}
