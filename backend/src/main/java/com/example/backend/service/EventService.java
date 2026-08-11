package com.example.backend.service;

import com.example.backend.dto.CreateEventRequest;
import com.example.backend.dto.EventDTO;

import java.util.List;
import java.util.UUID;

public interface EventService {
    List<EventDTO> getAllEvents(String username);

    EventDTO getEventById(UUID id);

    EventDTO createEvent(CreateEventRequest request, String username);

    void deleteEvent(UUID id, String username);
}
