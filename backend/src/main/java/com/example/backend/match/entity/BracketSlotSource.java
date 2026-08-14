package com.example.backend.match.entity;

import com.example.backend.enums.BracketSlot;
import com.example.backend.enums.SourceType;
import com.example.backend.event.entity.Pool;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "bracket_slot_sources")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BracketSlotSource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bracket_match_id", nullable = false)
    private Match bracketMatch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BracketSlot slot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_pool_id")
    private Pool sourcePool;

    @Column(name = "source_rank", nullable = false)
    private Integer sourceRank;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType = SourceType.POOL_RANK;

    @Column(name = "wildcard_rank")
    private Integer wildcardRank;
}
