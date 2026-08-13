package com.example.backend.tournament.service.impl;

import com.example.backend.dto.EventDTO;
import com.example.backend.tournament.dto.CreateTournamentRequest;
import com.example.backend.tournament.dto.TournamentDTO;
import com.example.backend.tournament.dto.UpdateTournamentRequest;
import com.example.backend.tournament.entity.Tournament;
import com.example.backend.tournament.mapper.TournamentMapper;
import com.example.backend.tournament.repository.TournamentRepository;
import com.example.backend.tournament.service.TournamentService;
import com.example.backend.user.entity.User;
import com.example.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TournamentServiceImpl implements TournamentService {

    private final TournamentRepository tournamentRepository;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
    private final TournamentMapper tournamentMapper;

    @Override
    public List<TournamentDTO> getAllTournaments(String username) {
        User owner = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return tournamentRepository.findByOwner(owner).stream()
                .map(tournamentMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public TournamentDTO getTournamentById(UUID id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));
        return tournamentMapper.toDto(tournament);
    }

    @Override
    public TournamentDTO createTournament(CreateTournamentRequest request, String username) {
        User owner = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Tournament tournament = modelMapper.map(request, Tournament.class);
        tournament.setOwner(owner);
        Tournament savedTournament = tournamentRepository.save(tournament);
        return tournamentMapper.toDto(savedTournament);
    }

    @Override
    public TournamentDTO updateTournament(UUID id, UpdateTournamentRequest request, String username) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));
        verifyOwnership(tournament, username);

        if (request.getName() != null)
            tournament.setName(request.getName());
        if (request.getDescription() != null)
            tournament.setDescription(request.getDescription());
        if (request.getStartDate() != null)
            tournament.setStartDate(request.getStartDate());
        if (request.getEndDate() != null)
            tournament.setEndDate(request.getEndDate());

        Tournament updatedTournament = tournamentRepository.save(tournament);
        return tournamentMapper.toDto(updatedTournament);
    }

    @Override
    public void deleteTournament(UUID id, String username) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));
        verifyOwnership(tournament, username);
        tournamentRepository.deleteById(id);
    }

    @Override
    public List<EventDTO> getEvents(UUID tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));
        return tournament.getEvents().stream()
                .map(e -> modelMapper.map(e, EventDTO.class))
                .collect(Collectors.toList());
    }

    private void verifyOwnership(Tournament tournament, String username) {
        if (tournament.getOwner() == null || !tournament.getOwner().getUsername().equals(username)) {
            throw new RuntimeException("You do not have permission to modify this tournament");
        }
    }
}
