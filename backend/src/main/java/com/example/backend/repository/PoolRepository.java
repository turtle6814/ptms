package com.example.backend.repository;

import com.example.backend.entity.Pool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PoolRepository extends JpaRepository<Pool, UUID> {
    List<Pool> findByTournamentId(UUID tournamentId);
}
