package com.mentia.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Atajos para tests de integración que recorren la API como lo haría el front: registrarse, iniciar sesión,
 * saltear la evaluación (como admin) y registrar intentos.
 */
@TestComponent
public class ApiClient {

    public static final String PASSWORD = "una-clave-larga";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public ApiClient(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    public record User(String username, UUID id, String token) {
    }

    public User newUser() throws Exception {
        String username = "u_" + UUID.randomUUID().toString().substring(0, 12).replace("-", "");
        JsonNode registered = json(mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "%s", "ageRange": "18-24"}""".formatted(username, PASSWORD)))
                .andExpect(status().isCreated()));
        return new User(username, UUID.fromString(registered.get("userId").asText()), login(username));
    }

    /** Lo hace admin (en la base) y saltea la evaluación: deja nivel 1 en las categorías del MVP. */
    public User skipAssessment(User user) throws Exception {
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", user.id());
        User admin = new User(user.username(), user.id(), login(user.username()));
        perform(admin, post("/api/v1/assessment/skip")).andExpect(status().isNoContent());
        return admin;
    }

    public String login(String username) throws Exception {
        return json(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "password": "%s"}""".formatted(username, PASSWORD))).andExpect(status().isOk()))
                .get("accessToken").asText();
    }

    public ResultActions perform(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()));
    }

    public ResultActions attempt(User user, String sessionId, String itemId, double accuracy, int responseTimeMs)
            throws Exception {
        return perform(user, post("/api/v1/sessions/{id}/attempts", sessionId).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"clientAttemptId": "%s", "sessionItemId": "%s", "isCorrect": %s, "accuracy": %s, "responseTimeMs": %d}"""
                        .formatted(UUID.randomUUID(), itemId, accuracy >= 0.5, accuracy, responseTimeMs)));
    }

    public JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
