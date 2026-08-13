package com.example.backend.match.repository;

import com.example.backend.match.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MatchRepository extends JpaRepository<Match, UUID> {
    List<Match> findByEventId(UUID eventId);

    List<Match> findByPoolId(UUID poolId);

    List<Match> findByWinnerNextMatch_Id(UUID matchId);
}
