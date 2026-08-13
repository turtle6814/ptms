package com.example.backend;

import com.example.backend.dto.AuthDtos.SignupRequest;
import com.example.backend.dto.CreateEventRequest;
import com.example.backend.dto.CreateTournamentRequest;
import com.example.backend.dto.PoolConfigDTO;
import com.example.backend.dto.ScoreUpdateRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Characterization tests for EventServiceImpl#generateEliminationBracket and
 * MatchServiceImpl#advanceInBracket/populateThirdPlaceMatch (see ERD.md order-of-work step 2).
 * These pin down today's actual seeding/advancement/bye/3rd-place behavior so a later rewrite
 * (ERD.md P1-P4) can be checked against it, not against what the behavior "should" be.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BracketAdvancementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String signup(String username, String phone) throws Exception {
        SignupRequest request = new SignupRequest();
        request.setUsername(username);
        request.setPhoneNumber(phone);
        request.setPassword("password123");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    private UUID createTournament(String token, String name) throws Exception {
        CreateTournamentRequest request = new CreateTournamentRequest();
        request.setName(name);
        request.setStartDate(LocalDate.now());
        request.setEndDate(LocalDate.now().plusDays(2));

        MvcResult result = mockMvc.perform(post("/api/v1/tournaments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText());
    }

    private JsonNode createEvent(String token, UUID tournamentId, String name, List<PoolConfigDTO> pools)
            throws Exception {
        CreateEventRequest request = new CreateEventRequest();
        request.setName(name);
        request.setTournamentId(tournamentId);
        request.setPools(pools);

        MvcResult result = mockMvc.perform(post("/api/v1/events")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode getEvent(UUID eventId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/events/" + eventId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private void submitScore(String token, UUID eventId, UUID matchId, int team1Score, int team2Score)
            throws Exception {
        ScoreUpdateRequest request = new ScoreUpdateRequest();
        request.setTeam1Score(team1Score);
        request.setTeam2Score(team2Score);

        mockMvc.perform(put("/api/v1/events/" + eventId + "/matches/" + matchId + "/score")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private static PoolConfigDTO pool(String name, String... teamNames) {
        PoolConfigDTO dto = new PoolConfigDTO();
        dto.setName(name);
        dto.setTeamNames(List.of(teamNames));
        return dto;
    }

    private static String teamId(JsonNode event, String teamName) {
        for (JsonNode team : event.path("teams")) {
            if (team.path("name").asText().equals(teamName)) {
                return team.path("id").asText();
            }
        }
        throw new IllegalStateException("Team not found: " + teamName);
    }

    private static UUID poolMatchId(JsonNode event, String poolName) {
        for (JsonNode pool : event.path("pools")) {
            if (pool.path("name").asText().equals(poolName)) {
                return UUID.fromString(pool.path("matches").get(0).path("id").asText());
            }
        }
        throw new IllegalStateException("Pool not found: " + poolName);
    }

    private static JsonNode bracketMatch(JsonNode event, int round, int position) {
        JsonNode rounds = event.path("eliminationBracket").path("rounds");
        for (JsonNode roundNode : rounds) {
            if (roundNode.path("roundNumber").asInt() != round) {
                continue;
            }
            for (JsonNode match : roundNode.path("matches")) {
                if (match.path("bracketPosition").asInt() == position) {
                    return match;
                }
            }
        }
        throw new IllegalStateException("Bracket match not found: round " + round + " position " + position);
    }

    private static void assertTeams(JsonNode match, String expectedTeam1Id, String expectedTeam2Id) {
        assertEquals(expectedTeam1Id, match.path("team1Id").asText());
        assertEquals(expectedTeam2Id, match.path("team2Id").asText());
    }

    @Test
    void singlePoolSkipsSemisAndThirdPlace() throws Exception {
        String token = signup("bracket1pool", "+19990000001");
        UUID tournamentId = createTournament(token, "Bracket 1-Pool Tournament");

        UUID eventId = UUID.fromString(createEvent(token, tournamentId, "1 Pool Event",
                List.of(pool("Pool A", "A1", "A2"))).path("id").asText());
        // Pool matches aren't hydrated on the create-event response (Pool.matches is never
        // appended to in-memory in generateRoundRobinMatches), so re-fetch.
        JsonNode created = getEvent(eventId);

        // Generation: a single pool produces exactly one bracket round with one match, no 3rd place.
        assertEquals(1, created.path("eliminationBracket").path("rounds").size());
        JsonNode finalMatch = bracketMatch(created, 1, 1);
        assertTrue(created.path("eliminationBracket").path("thirdPlaceMatch").isNull());

        String a1 = teamId(created, "A1");
        String a2 = teamId(created, "A2");
        UUID matchId = UUID.fromString(finalMatch.path("id").asText());

        submitScore(token, eventId, poolMatchId(created, "Pool A"), 11, 5);

        // Seeding: with a single pool, seed1/seed2 go straight into the final, no cross-seeding.
        JsonNode afterSeeding = getEvent(eventId);
        assertTeams(bracketMatch(afterSeeding, 1, 1), a1, a2);

        submitScore(token, eventId, matchId, 15, 10);

        JsonNode finalState = getEvent(eventId);
        assertEquals(a1, finalState.path("eliminationBracket").path("champion").asText());
        assertTrue(finalState.path("eliminationBracket").path("thirdPlaceMatch").isNull());
    }

    @Test
    void twoPoolsCrossSeedAndPopulateThirdPlace() throws Exception {
        String token = signup("bracket2pool", "+19990000002");
        UUID tournamentId = createTournament(token, "Bracket 2-Pool Tournament");
        UUID eventId = UUID.fromString(createEvent(token, tournamentId, "2 Pool Event",
                List.of(pool("Pool A", "A1", "A2"), pool("Pool B", "B1", "B2"))).path("id").asText());
        JsonNode created = getEvent(eventId);

        assertEquals(2, created.path("eliminationBracket").path("rounds").size());

        String a1 = teamId(created, "A1");
        String a2 = teamId(created, "A2");
        String b1 = teamId(created, "B1");
        String b2 = teamId(created, "B2");

        submitScore(token, eventId, poolMatchId(created, "Pool A"), 11, 5); // A1 seed1, A2 seed2
        submitScore(token, eventId, poolMatchId(created, "Pool B"), 11, 5); // B1 seed1, B2 seed2

        // Cross-seeding: match position p's team2 comes from pool index (p mod n)'s seed2.
        JsonNode seeded = getEvent(eventId);
        assertTeams(bracketMatch(seeded, 1, 1), a1, b2);
        assertTeams(bracketMatch(seeded, 1, 2), b1, a2);

        UUID r1pos1 = UUID.fromString(bracketMatch(seeded, 1, 1).path("id").asText());
        UUID r1pos2 = UUID.fromString(bracketMatch(seeded, 1, 2).path("id").asText());

        submitScore(token, eventId, r1pos1, 15, 10); // A1 beats B2
        submitScore(token, eventId, r1pos2, 15, 10); // B1 beats A2

        // Round 1 IS the semifinal round here (maxRound-1 == 1), so both losers populate 3rd place,
        // in submission order.
        JsonNode advanced = getEvent(eventId);
        assertTeams(bracketMatch(advanced, 2, 1), a1, b1);
        JsonNode thirdPlace = advanced.path("eliminationBracket").path("thirdPlaceMatch");
        assertEquals(b2, thirdPlace.path("team1Id").asText());
        assertEquals(a2, thirdPlace.path("team2Id").asText());

        UUID finalMatchId = UUID.fromString(bracketMatch(advanced, 2, 1).path("id").asText());
        UUID thirdPlaceMatchId = UUID.fromString(thirdPlace.path("id").asText());

        submitScore(token, eventId, finalMatchId, 15, 10); // A1 beats B1
        submitScore(token, eventId, thirdPlaceMatchId, 15, 10); // B2 beats A2

        JsonNode finalState = getEvent(eventId);
        assertEquals(a1, finalState.path("eliminationBracket").path("champion").asText());
        assertEquals(b2, finalState.path("eliminationBracket").path("thirdPlaceTeamId").asText());
    }

    @Test
    void threePoolsByeSkipsThirdPlaceContribution() throws Exception {
        String token = signup("bracket3pool", "+19990000003");
        UUID tournamentId = createTournament(token, "Bracket 3-Pool Tournament");
        UUID eventId = UUID.fromString(createEvent(token, tournamentId, "3 Pool Event",
                List.of(pool("Pool A", "A1", "A2"), pool("Pool B", "B1", "B2"), pool("Pool C", "C1", "C2")))
                .path("id").asText());
        JsonNode created = getEvent(eventId);

        // Generation: 3 -> 2 -> 1, three rounds, 3rd place at (round 3, position 2).
        assertEquals(3, created.path("eliminationBracket").path("rounds").size());

        String a1 = teamId(created, "A1");
        String b1 = teamId(created, "B1");
        String c1 = teamId(created, "C1");

        submitScore(token, eventId, poolMatchId(created, "Pool A"), 11, 5);
        submitScore(token, eventId, poolMatchId(created, "Pool B"), 11, 5);
        submitScore(token, eventId, poolMatchId(created, "Pool C"), 11, 5);

        // Cross-seeding: match position p's team2 comes from pool index (p mod n)'s seed2.
        JsonNode seeded = getEvent(eventId);
        assertTeams(bracketMatch(seeded, 1, 1), a1, teamId(seeded, "B2"));
        assertTeams(bracketMatch(seeded, 1, 2), b1, teamId(seeded, "C2"));
        assertTeams(bracketMatch(seeded, 1, 3), c1, teamId(seeded, "A2"));

        UUID r1pos1 = UUID.fromString(bracketMatch(seeded, 1, 1).path("id").asText());
        UUID r1pos2 = UUID.fromString(bracketMatch(seeded, 1, 2).path("id").asText());
        UUID r1pos3 = UUID.fromString(bracketMatch(seeded, 1, 3).path("id").asText());

        submitScore(token, eventId, r1pos1, 15, 10); // A1 wins
        submitScore(token, eventId, r1pos2, 15, 10); // B1 wins
        submitScore(token, eventId, r1pos3, 15, 10); // C1 wins -> triggers the bye

        // Bye: position 3 is the last, odd-positioned match of an odd-sized (3) round, so its
        // winner auto-advances through an auto-completed Round-2 position-2 match straight into
        // the final, contributing nothing to 3rd place (its synthetic loser is null).
        JsonNode afterR1 = getEvent(eventId);
        JsonNode round2pos2 = bracketMatch(afterR1, 2, 2);
        assertEquals("COMPLETED", round2pos2.path("status").asText());
        assertEquals(c1, round2pos2.path("winnerId").asText());
        assertEquals(c1, round2pos2.path("team1Id").asText());
        assertTrue(round2pos2.path("team2Id").isNull());
        assertEquals(c1, bracketMatch(afterR1, 3, 1).path("team2Id").asText());
        assertTrue(bracketMatch(afterR1, 3, 1).path("team1Id").isNull());
        assertTeams(bracketMatch(afterR1, 2, 1), a1, b1);

        UUID r2pos1 = UUID.fromString(bracketMatch(afterR1, 2, 1).path("id").asText());
        submitScore(token, eventId, r2pos1, 15, 10); // A1 wins the real semifinal

        JsonNode afterR2 = getEvent(eventId);
        assertTeams(bracketMatch(afterR2, 3, 1), a1, c1);
        JsonNode thirdPlace = afterR2.path("eliminationBracket").path("thirdPlaceMatch");
        assertEquals(b1, thirdPlace.path("team1Id").asText());
        assertTrue(thirdPlace.path("team2Id").isNull());

        UUID finalMatchId = UUID.fromString(bracketMatch(afterR2, 3, 1).path("id").asText());
        submitScore(token, eventId, finalMatchId, 15, 10); // A1 champion

        JsonNode finalState = getEvent(eventId);
        assertEquals(a1, finalState.path("eliminationBracket").path("champion").asText());
        assertTrue(finalState.path("eliminationBracket").path("thirdPlaceTeamId").isNull());
    }

    @Test
    void fourPoolsEvenBracketNoByes() throws Exception {
        String token = signup("bracket4pool", "+19990000004");
        UUID tournamentId = createTournament(token, "Bracket 4-Pool Tournament");
        UUID eventId = UUID.fromString(createEvent(token, tournamentId, "4 Pool Event",
                List.of(pool("Pool A", "A1", "A2"), pool("Pool B", "B1", "B2"),
                        pool("Pool C", "C1", "C2"), pool("Pool D", "D1", "D2")))
                .path("id").asText());
        JsonNode created = getEvent(eventId);

        assertEquals(3, created.path("eliminationBracket").path("rounds").size());

        submitScore(token, eventId, poolMatchId(created, "Pool A"), 11, 5);
        submitScore(token, eventId, poolMatchId(created, "Pool B"), 11, 5);
        submitScore(token, eventId, poolMatchId(created, "Pool C"), 11, 5);
        submitScore(token, eventId, poolMatchId(created, "Pool D"), 11, 5);

        JsonNode seeded = getEvent(eventId);
        String a1 = teamId(seeded, "A1");
        String b1 = teamId(seeded, "B1");
        String c1 = teamId(seeded, "C1");
        String d1 = teamId(seeded, "D1");
        assertTeams(bracketMatch(seeded, 1, 1), a1, teamId(seeded, "B2"));
        assertTeams(bracketMatch(seeded, 1, 2), b1, teamId(seeded, "C2"));
        assertTeams(bracketMatch(seeded, 1, 3), c1, teamId(seeded, "D2"));
        assertTeams(bracketMatch(seeded, 1, 4), d1, teamId(seeded, "A2"));

        submitScore(token, eventId, UUID.fromString(bracketMatch(seeded, 1, 1).path("id").asText()), 15, 10);
        submitScore(token, eventId, UUID.fromString(bracketMatch(seeded, 1, 2).path("id").asText()), 15, 10);
        submitScore(token, eventId, UUID.fromString(bracketMatch(seeded, 1, 3).path("id").asText()), 15, 10);
        submitScore(token, eventId, UUID.fromString(bracketMatch(seeded, 1, 4).path("id").asText()), 15, 10);

        // No byes anywhere: 4 -> 2 -> 1 is even at every step.
        JsonNode afterR1 = getEvent(eventId);
        assertTeams(bracketMatch(afterR1, 2, 1), a1, b1);
        assertTeams(bracketMatch(afterR1, 2, 2), c1, d1);

        submitScore(token, eventId, UUID.fromString(bracketMatch(afterR1, 2, 1).path("id").asText()), 15, 10);
        submitScore(token, eventId, UUID.fromString(bracketMatch(afterR1, 2, 2).path("id").asText()), 15, 10);

        JsonNode afterR2 = getEvent(eventId);
        assertTeams(bracketMatch(afterR2, 3, 1), a1, c1);
        JsonNode thirdPlace = afterR2.path("eliminationBracket").path("thirdPlaceMatch");
        assertEquals(b1, thirdPlace.path("team1Id").asText());
        assertEquals(d1, thirdPlace.path("team2Id").asText());

        UUID finalMatchId = UUID.fromString(bracketMatch(afterR2, 3, 1).path("id").asText());
        UUID thirdPlaceMatchId = UUID.fromString(thirdPlace.path("id").asText());
        submitScore(token, eventId, finalMatchId, 15, 10); // A1 champion
        submitScore(token, eventId, thirdPlaceMatchId, 15, 10); // B1 takes 3rd

        JsonNode finalState = getEvent(eventId);
        assertEquals(a1, finalState.path("eliminationBracket").path("champion").asText());
        assertEquals(b1, finalState.path("eliminationBracket").path("thirdPlaceTeamId").asText());
    }
}
