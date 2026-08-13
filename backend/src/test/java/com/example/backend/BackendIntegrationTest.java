package com.example.backend;

import com.example.backend.dto.AuthDtos.LoginRequest;
import com.example.backend.dto.AuthDtos.SignupRequest;
import com.example.backend.dto.CreateEventRequest;
import com.example.backend.dto.CreateTournamentRequest;
import com.example.backend.dto.PoolConfigDTO;
import com.example.backend.dto.ScoreUpdateRequest;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BackendIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String token;
    private static UUID tournamentId;
    private static UUID eventId;

    @Test
    @Order(1)
    void testSignupAndLogin() throws Exception {
        SignupRequest signupRequest = new SignupRequest();
        signupRequest.setUsername("testuser");
        signupRequest.setPassword("password123");
        signupRequest.setPhoneNumber("+1234567890");

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.token").exists());

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setPhoneNumber("+1234567890");
        loginRequest.setPassword("password123");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        token = objectMapper.readTree(response).path("data").path("token").asText();
    }

    @Test
    @Order(2)
    void testCreateTournament() throws Exception {
        CreateTournamentRequest tournamentRequest = new CreateTournamentRequest();
        tournamentRequest.setName("Test Tournament");
        tournamentRequest.setStartDate(LocalDate.now());
        tournamentRequest.setEndDate(LocalDate.now().plusDays(2));

        MvcResult result = mockMvc.perform(post("/api/v1/tournaments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tournamentRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        tournamentId = UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());
    }

    @Test
    @Order(3)
    void testCreateEvent() throws Exception {
        PoolConfigDTO poolA = new PoolConfigDTO();
        poolA.setName("Pool A");
        poolA.setTeamNames(List.of("Team 1", "Team 2", "Team 3"));

        CreateEventRequest request = new CreateEventRequest();
        request.setName("Test Event");
        request.setTournamentId(tournamentId);
        request.setPools(Collections.singletonList(poolA));

        MvcResult result = mockMvc.perform(post("/api/v1/events")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.pools").isArray())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        eventId = UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());
    }

    @Test
    @Order(4)
    void testGetTournaments() throws Exception {
        mockMvc.perform(get("/api/v1/tournaments")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(5)
    void testScoreSubmissionEnforcesRules() throws Exception {
        MvcResult eventResult = mockMvc.perform(get("/api/v1/events/" + eventId))
                .andExpect(status().isOk())
                .andReturn();
        String eventJson = eventResult.getResponse().getContentAsString();
        String matchId = objectMapper.readTree(eventJson)
                .path("data").path("pools").get(0).path("matches").get(0).path("id").asText();

        ScoreUpdateRequest belowTarget = new ScoreUpdateRequest();
        belowTarget.setTeam1Score(5);
        belowTarget.setTeam2Score(3);
        mockMvc.perform(put("/api/v1/events/" + eventId + "/matches/" + matchId + "/score")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(belowTarget)))
                .andExpect(status().isUnprocessableEntity());

        ScoreUpdateRequest validScore = new ScoreUpdateRequest();
        validScore.setTeam1Score(11);
        validScore.setTeam2Score(9);
        mockMvc.perform(put("/api/v1/events/" + eventId + "/matches/" + matchId + "/score")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validScore)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(6)
    void testRoundRobinOnlyEventSkipsBracket() throws Exception {
        PoolConfigDTO poolA = new PoolConfigDTO();
        poolA.setName("Pool A");
        poolA.setTeamNames(List.of("RR Team 1", "RR Team 2"));

        CreateEventRequest request = new CreateEventRequest();
        request.setName("Round Robin Only Event");
        request.setTournamentId(tournamentId);
        request.setPools(Collections.singletonList(poolA));
        request.setFormat(com.example.backend.enums.EventFormat.ROUND_ROBIN_ONLY);

        mockMvc.perform(post("/api/v1/events")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.format").value("ROUND_ROBIN_ONLY"))
                .andExpect(jsonPath("$.data.eliminationBracket").doesNotExist());
    }

    @Test
    @Order(7)
    void testDuplicateTeamNameInSameEventIsRejected() throws Exception {
        PoolConfigDTO poolA = new PoolConfigDTO();
        poolA.setName("Dup Pool A");
        poolA.setTeamNames(List.of("Dup Team"));

        PoolConfigDTO poolB = new PoolConfigDTO();
        poolB.setName("Dup Pool B");
        poolB.setTeamNames(List.of("dup team"));

        CreateEventRequest request = new CreateEventRequest();
        request.setName("Duplicate Team Name Event");
        request.setTournamentId(tournamentId);
        request.setPools(List.of(poolA, poolB));

        mockMvc.perform(post("/api/v1/events")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
