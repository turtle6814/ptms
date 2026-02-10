package com.example.backend;

import com.example.backend.dto.AuthDtos.LoginRequest;
import com.example.backend.dto.AuthDtos.SignupRequest;
import com.example.backend.dto.CreateEventRequest;
import com.example.backend.dto.CreateTournamentRequest;
import com.example.backend.dto.PoolConfigDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
    private static UUID eventId;
    private static UUID tournamentId;

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
    void testCreateEvent() throws Exception {
        CreateEventRequest eventRequest = new CreateEventRequest();
        eventRequest.setName("Test Event");
        eventRequest.setStartDate(LocalDate.now());
        eventRequest.setEndDate(LocalDate.now().plusDays(2));

        MvcResult result = mockMvc.perform(post("/api/v1/events")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(eventRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        eventId = UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());
    }

    @Test
    @Order(3)
    void testCreateTournament() throws Exception {
        PoolConfigDTO poolA = new PoolConfigDTO();
        poolA.setName("Pool A");
        poolA.setTeamNames(List.of("Team 1", "Team 2", "Team 3"));

        CreateTournamentRequest request = new CreateTournamentRequest();
        request.setName("Test Tournament");
        request.setEventId(eventId);
        request.setPools(Collections.singletonList(poolA));

        MvcResult result = mockMvc.perform(post("/api/v1/tournaments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.pools").isArray())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        tournamentId = UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());
    }

    @Test
    @Order(4)
    void testGetEvents() throws Exception {
        mockMvc.perform(get("/api/v1/events")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
