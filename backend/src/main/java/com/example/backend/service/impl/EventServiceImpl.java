package com.example.backend.service.impl;

import com.example.backend.dto.*;
import com.example.backend.entity.Event;
import com.example.backend.entity.Tournament;
import com.example.backend.repository.EventRepository;
import com.example.backend.repository.TournamentRepository;
import com.example.backend.service.EventService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final TournamentRepository tournamentRepository;
    private final ModelMapper modelMapper;

    @Override
    public List<EventDTO> getAllEvents() {
        return eventRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public EventDTO getEventById(UUID id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        return convertToDTO(event);
    }

    @Override
    public EventDTO createEvent(CreateEventRequest request) {
        Event event = modelMapper.map(request, Event.class);
        // CreatedAt/UpdatedAt handled by JPA annotations
        Event savedEvent = eventRepository.save(event);
        return convertToDTO(savedEvent);
    }

    @Override
    public EventDTO updateEvent(UUID id, UpdateEventRequest request) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        if (request.getName() != null)
            event.setName(request.getName());
        if (request.getDescription() != null)
            event.setDescription(request.getDescription());
        if (request.getStartDate() != null)
            event.setStartDate(request.getStartDate());
        if (request.getEndDate() != null)
            event.setEndDate(request.getEndDate());

        Event updatedEvent = eventRepository.save(event);
        return convertToDTO(updatedEvent);
    }

    @Override
    public void deleteEvent(UUID id) {
        if (!eventRepository.existsById(id)) {
            throw new RuntimeException("Event not found");
        }
        eventRepository.deleteById(id);
    }

    @Override
    public List<TournamentDTO> getTournaments(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        return event.getTournaments().stream()
                .map(t -> modelMapper.map(t, TournamentDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventDTO addTournamentToEvent(UUID eventId, UUID tournamentId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));

        tournament.setEvent(event);
        tournamentRepository.save(tournament);

        // Refresh event to include new tournament
        return getEventById(eventId);
    }

    @Override
    @Transactional
    public EventDTO removeTournamentFromEvent(UUID eventId, UUID tournamentId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));

        if (tournament.getEvent() != null && tournament.getEvent().getId().equals(eventId)) {
            tournament.setEvent(null);
            tournamentRepository.save(tournament);
        }

        return getEventById(eventId);
    }

    private EventDTO convertToDTO(Event event) {
        EventDTO dto = modelMapper.map(event, EventDTO.class);
        if (event.getTournaments() != null) {
            dto.setTournamentIds(event.getTournaments().stream()
                    .map(Tournament::getId)
                    .collect(Collectors.toList()));
        }
        return dto;
    }
}
