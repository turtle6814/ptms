package com.example.backend.match.entity;

import com.example.backend.enums.BracketSlot;
import com.example.backend.enums.BracketType;
import com.example.backend.enums.MatchStatus;
import com.example.backend.enums.MatchType;
import com.example.backend.event.entity.Event;
import com.example.backend.event.entity.Pool;
import com.example.backend.event.entity.Team;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "matches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pool_id")
    private Pool pool;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false)
    private MatchType matchType = MatchType.POOL;

    @Column(name = "round_number")
    private Integer roundNumber;

    @Column(name = "bracket_round")
    private Integer bracketRound;

    @Column(name = "bracket_position")
    private Integer bracketPosition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_next_match_id")
    private Match winnerNextMatch;

    @Enumerated(EnumType.STRING)
    @Column(name = "winner_next_slot")
    private BracketSlot winnerNextSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loser_next_match_id")
    private Match loserNextMatch;

    @Enumerated(EnumType.STRING)
    @Column(name = "loser_next_slot")
    private BracketSlot loserNextSlot;

    @Enumerated(EnumType.STRING)
    @Column(name = "bracket_type")
    private BracketType bracketType;

    // Only populated for wildcard-sourced slots (no sourcePool to cascade-persist through) -
    // pool-sourced slots still cascade via Pool.bracketSlotSources, unchanged.
    @Builder.Default
    @OneToMany(mappedBy = "bracketMatch", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BracketSlotSource> bracketSlotSources = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team1_id")
    private Team team1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team2_id")
    private Team team2;

    private Integer team1Score;
    private Integer team2Score;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private Team winner;

    @Builder.Default
    @Column(name = "target_score", nullable = false)
    private Integer targetScore = 11;

    @Builder.Default
    @Column(name = "win_by_two")
    private Boolean winByTwo = true;

    @Builder.Default
    @Column(name = "score_cap", nullable = false)
    private Integer scoreCap = 15;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus status = MatchStatus.PENDING;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
