package com.example.backend.repository;

import com.example.backend.entity.PoolStanding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PoolStandingRepository extends JpaRepository<PoolStanding, UUID> {
    List<PoolStanding> findByPoolId(UUID poolId);

    Optional<PoolStanding> findByPoolIdAndTeamId(UUID poolId, UUID teamId);
}
