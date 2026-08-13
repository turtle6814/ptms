package com.example.backend.repository;

import com.example.backend.entity.BracketSlotSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BracketSlotSourceRepository extends JpaRepository<BracketSlotSource, UUID> {
    List<BracketSlotSource> findBySourcePoolIdAndSourceRank(UUID sourcePoolId, int sourceRank);
}
