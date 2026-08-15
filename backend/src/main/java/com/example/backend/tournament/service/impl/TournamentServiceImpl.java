package com.example.backend.tournament.service.impl;

import com.example.backend.base.BaseService;
import com.example.backend.event.dto.response.EventResponse;
import com.example.backend.tournament.dto.request.CreateTournamentRequest;
import com.example.backend.tournament.dto.response.TournamentResponse;
import com.example.backend.tournament.dto.request.UpdateTournamentRequest;
import com.example.backend.tournament.entity.Tournament;
import com.example.backend.tournament.mapper.TournamentMapper;
import com.example.backend.tournament.repository.TournamentRepository;
import com.example.backend.tournament.service.TournamentService;
import com.example.backend.user.entity.User;
import com.example.backend.user.repository.UserRepository;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TournamentServiceImpl extends BaseService<Tournament, UUID, TournamentResponse> implements TournamentService {

    private final TournamentRepository tournamentRepository;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
    private final TournamentMapper tournamentMapper;

    public TournamentServiceImpl(TournamentRepository tournamentRepository, UserRepository userRepository,
                                  ModelMapper modelMapper, TournamentMapper tournamentMapper) {
        super(tournamentRepository, tournamentMapper::toResponse, "Tournament");
        this.tournamentRepository = tournamentRepository;
        this.userRepository = userRepository;
        this.modelMapper = modelMapper;
        this.tournamentMapper = tournamentMapper;
    }

    @Override
    public List<TournamentResponse> getAllTournaments(String username) {
        User owner = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return tournamentRepository.findByOwner(owner).stream()
                .map(tournamentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public TournamentResponse getTournamentById(UUID id) {
        return getById(id);
    }

    @Override
    public TournamentResponse createTournament(CreateTournamentRequest request, String username) {
        User owner = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Tournament tournament = modelMapper.map(request, Tournament.class);
        tournament.setOwner(owner);
        Tournament savedTournament = save(tournament);
        return tournamentMapper.toResponse(savedTournament);
    }

    @Override
    public TournamentResponse updateTournament(UUID id, UpdateTournamentRequest request, String username) {
        Tournament tournament = findByIdOrThrow(id);
        verifyOwnership(tournament, username);

        if (request.getName() != null)
            tournament.setName(request.getName());
        if (request.getDescription() != null)
            tournament.setDescription(request.getDescription());

        Tournament updatedTournament = save(tournament);
        return tournamentMapper.toResponse(updatedTournament);
    }

    @Override
    public void deleteTournament(UUID id, String username) {
        Tournament tournament = findByIdOrThrow(id);
        verifyOwnership(tournament, username);
        deleteById(id);
    }

    @Override
    public List<EventResponse> getEvents(UUID tournamentId) {
        Tournament tournament = findByIdOrThrow(tournamentId);
        return tournament.getEvents().stream()
                .map(e -> modelMapper.map(e, EventResponse.class))
                .collect(Collectors.toList());
    }

    private void verifyOwnership(Tournament tournament, String username) {
        if (tournament.getOwner() == null || !tournament.getOwner().getUsername().equals(username)) {
            throw new RuntimeException("You do not have permission to modify this tournament");
        }
    }
}
