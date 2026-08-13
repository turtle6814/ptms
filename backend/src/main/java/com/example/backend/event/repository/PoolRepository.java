package com.example.backend.event.repository;

import com.example.backend.event.entity.Pool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PoolRepository extends JpaRepository<Pool, UUID> {
    List<Pool> findByEventId(UUID eventId);
}
