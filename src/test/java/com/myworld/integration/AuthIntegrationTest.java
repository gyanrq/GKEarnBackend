package com.myworld.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FIX 7: Integration tests with Testcontainers — real PostgreSQL, full Spring context.
 *
 * What this tests:
 *   - Full request → controller → service → JPA → real PostgreSQL flow
 *   - Flyway migrations (V1, V2, V3) run on clean DB before tests
 *   - Spring Security filter chain (JWT auth, rate limit filter, api version filter)
 *   - API versioning (/api/v1/* rewrites correctly)
 *
 * Prerequisites: Docker must be running.
 * Run: mvn verify -Dspring.profiles.active=test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Auth API — Integration Tests")
class AuthIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("earnx_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired MockMvc mockMvc;

    private static String registerBody(String email, String phone) {
        return """
                {"name":"Test User","email":"%s","phone":"%s",
                 "password":"TestPass@123","referralCode":null}
                """.formatted(email, phone);
    }

    @Test
    @DisplayName("POST /api/auth/register — new user should register successfully")
    void register_newUser_returns200() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("newuser@test.com", "9876543210")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register — versioned path should work identically (FIX 4)")
    void register_versionedPath_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("versioned@test.com", "9876543211")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/auth/login — wrong password should return 401")
    void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@test.com\",\"password\":\"WrongPass\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/register — duplicate email should return 4xx")
    void register_duplicateEmail_returnsError() throws Exception {
        String body = registerBody("duplicate@test.com", "9876543212");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("POST /api/auth/register — missing fields should return 400")
    void register_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/user/profile — without JWT should return 401")
    void protectedEndpoint_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/user/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/user/profile — versioned protected route without JWT returns 401")
    void versionedProtectedEndpoint_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/user/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /actuator/health — public endpoint should return UP")
    void actuatorHealth_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
