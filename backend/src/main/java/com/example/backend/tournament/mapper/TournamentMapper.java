package com.example.backend.tournament.mapper;

import com.example.backend.event.entity.Event;
import com.example.backend.tournament.dto.TournamentDTO;
import com.example.backend.tournament.entity.Tournament;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TournamentMapper {

    private final ModelMapper modelMapper;

    public TournamentDTO toDto(Tournament tournament) {
        TournamentDTO dto = modelMapper.map(tournament, TournamentDTO.class);
        if (tournament.getEvents() != null) {
            dto.setEventIds(tournament.getEvents().stream()
                    .map(Event::getId)
                    .collect(Collectors.toList()));
        }
        return dto;
    }
}
