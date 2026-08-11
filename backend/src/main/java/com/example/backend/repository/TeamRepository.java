package com.example.backend.repository;

import com.example.backend.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TeamRepository extends JpaRepository<Team, UUID> {
    List<Team> findByEventId(UUID eventId);

    List<Team> findByPoolId(UUID poolId);
}
