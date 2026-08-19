package com.example.backend.event.service;

import com.example.backend.event.dto.request.CreateEventRequest;
import com.example.backend.event.dto.response.EventRefereeResponse;
import com.example.backend.event.dto.response.EventResponse;

import java.util.List;
import java.util.UUID;

public interface EventService {
    List<EventResponse> getAllEvents(String username);

    EventResponse getEventById(UUID id);

    EventResponse createEvent(CreateEventRequest request, String username);

    void deleteEvent(UUID id, String username);

    List<EventRefereeResponse> getReferees(UUID eventId, String actingUsername);

    EventRefereeResponse assignReferee(UUID eventId, UUID userId, String actingUsername);

    void unassignReferee(UUID eventId, UUID userId, String actingUsername);

    List<EventResponse> getAssignedEvents(String username);
}
