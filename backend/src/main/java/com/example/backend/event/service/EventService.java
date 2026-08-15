package com.example.backend.event.service;

import com.example.backend.event.dto.request.CreateEventRequest;
import com.example.backend.event.dto.response.EventResponse;

import java.util.List;
import java.util.UUID;

public interface EventService {
    List<EventResponse> getAllEvents(String username);

    EventResponse getEventById(UUID id);

    EventResponse createEvent(CreateEventRequest request, String username);

    void deleteEvent(UUID id, String username);
}
