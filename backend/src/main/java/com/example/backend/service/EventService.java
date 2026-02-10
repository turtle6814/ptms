package com.example.backend.service;

import com.example.backend.dto.*;

import java.util.List;
import java.util.UUID;

public interface EventService {
    List<EventDTO> getAllEvents();

    EventDTO getEventById(UUID id);

    EventDTO createEvent(CreateEventRequest request);

    EventDTO updateEvent(UUID id, UpdateEventRequest request);

    void deleteEvent(UUID id);

    List<TournamentDTO> getTournaments(UUID eventId);

    EventDTO addTournamentToEvent(UUID eventId, UUID tournamentId);

    EventDTO removeTournamentFromEvent(UUID eventId, UUID tournamentId);
}
