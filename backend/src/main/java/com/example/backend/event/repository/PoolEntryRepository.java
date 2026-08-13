package com.example.backend.event.repository;

import com.example.backend.event.entity.PoolEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PoolEntryRepository extends JpaRepository<PoolEntry, UUID> {
    List<PoolEntry> findByPoolId(UUID poolId);
}
