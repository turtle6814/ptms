package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "pool_standings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PoolStanding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pool_id", nullable = false)
    private Pool pool;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    private int wins;
    private int losses;
    private int pointsFor;
    private int pointsAgainst;

    // Calculated as pointsFor - pointsAgainst
    private int pointDifferential;
}
