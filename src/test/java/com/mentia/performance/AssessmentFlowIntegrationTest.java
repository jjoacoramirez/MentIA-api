package com.mentia.performance;

import static com.mentia.support.Contract.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentia.session.AssessmentCompletedEvent;
import com.mentia.support.IntegrationTest;

@RecordApplicationEvents
class AssessmentFlowIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "una-clave-larga";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationEvents events;

    String username;
    String token;

    @BeforeEach
    void setUp() throws Exception {
        username = "eval_" + UUID.randomUUID().toString().substring(0, 8);
        register(username);
        token = login(username);
    }

    @Test
    void evaluacionCompletaFijaElNivelInicialPorCategoria() throws Exception {
        JsonNode session = json(auth(post("/api/v1/assessment/start"))
                .andExpect(status().isCreated())
                .andExpect(matchesContract()));
        String sessionId = session.get("id").asText();

        // Dos ejercicios por cada categoría del MVP, todos en nivel 3, con contenido de la variante.
        List<JsonNode> items = list(session.get("items"));
        assertThat(items).hasSize(6);
        assertThat(items).allSatisfy(item -> {
            assertThat(item.get("difficultyLevel").asInt()).isEqualTo(3);
            assertThat(item.get("content").isEmpty()).isFalse();
            assertThat(item.get("params").isEmpty()).isFalse();
        });
        Map<String, Long> perCategory = new HashMap<>();
        items.forEach(i -> perCategory.merge(i.get("category").asText(), 1L, Long::sum));
        assertThat(perCategory).containsOnly(
                Map.entry("ATENCION", 2L), Map.entry("MEMORIA_VISUAL", 2L), Map.entry("MEMORIA_TRABAJO", 2L));
        assertThat(session.get("estimatedDurationSeconds").asInt()).isPositive();

        // Pedirla de nuevo la retoma.
        assertThat(json(auth(post("/api/v1/assessment/start")).andExpect(status().isOk())).get("id").asText())
                .isEqualTo(sessionId);

        auth(post("/api/v1/sessions/{id}/start", sessionId)).andExpect(status().isOk()).andExpect(matchesContract());

        // ATENCION 0.95 → 5, MEMORIA_VISUAL 0.7 → 3, MEMORIA_TRABAJO 0.3 → 1.
        Map<String, Double> accuracyByCategory = Map.of("ATENCION", 0.95, "MEMORIA_VISUAL", 0.7, "MEMORIA_TRABAJO", 0.3);
        for (JsonNode item : items) {
            double accuracy = accuracyByCategory.get(item.get("category").asText());
            attempt(sessionId, item.get("sessionItemId").asText(), accuracy).andExpect(status().isCreated());
        }

        JsonNode result = json(auth(post("/api/v1/sessions/{id}/complete", sessionId))
                .andExpect(status().isOk())
                .andExpect(matchesContract()));
        assertThat(result.get("itemsCompleted").asInt()).isEqualTo(6);
        Map<String, Integer> levels = new HashMap<>();
        list(result.get("categories")).forEach(c -> {
            assertThat(c.get("difficultyLevelBefore").asInt()).isEqualTo(3);
            levels.put(c.get("category").asText(), c.get("difficultyLevelAfter").asInt());
        });
        assertThat(levels).containsOnly(
                Map.entry("ATENCION", 5), Map.entry("MEMORIA_VISUAL", 3), Map.entry("MEMORIA_TRABAJO", 1));

        assertThat(jdbc.queryForList("""
                SELECT c.code, p.level, p.score, p.samples FROM performance_profiles p
                JOIN categories c ON c.id = p.category_id JOIN users u ON u.id = p.user_id
                WHERE u.username = ? ORDER BY c.code""", username))
                .extracting(r -> r.get("code") + ":" + r.get("level") + ":" + Math.round((Double) r.get("score"))
                        + ":" + r.get("samples"))
                .containsExactly("ATENCION:5:95:2", "MEMORIA_TRABAJO:1:30:2", "MEMORIA_VISUAL:3:70:2");

        auth(get("/api/v1/profile")).andExpect(jsonPath("$.assessmentCompleted").value(true));
        assertThat(events.stream(AssessmentCompletedEvent.class)).hasSize(1);

        // Una sesión cerrada no se cierra de nuevo ni acepta intentos.
        auth(post("/api/v1/sessions/{id}/complete", sessionId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"));
    }

    @Test
    void restartAbandonaLaEvaluacionAnteriorYCreaOtra() throws Exception {
        String first = json(auth(post("/api/v1/assessment/start")).andExpect(status().isCreated())).get("id").asText();

        String second = json(auth(post("/api/v1/assessment/start").param("restart", "true"))
                .andExpect(status().isCreated())).get("id").asText();

        assertThat(second).isNotEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT status FROM training_sessions WHERE id = ?::uuid", String.class, first))
                .isEqualTo("ABANDONED");
    }

    @Test
    void unaEvaluacionVencidaNoSeRetoma() throws Exception {
        String old = json(auth(post("/api/v1/assessment/start")).andExpect(status().isCreated())).get("id").asText();
        jdbc.update("UPDATE training_sessions SET created_at = ? WHERE id = ?::uuid",
                Timestamp.from(Instant.now().minus(13, ChronoUnit.HOURS)), old);

        String fresh = json(auth(post("/api/v1/assessment/start")).andExpect(status().isCreated())).get("id").asText();

        assertThat(fresh).isNotEqualTo(old);
        assertThat(jdbc.queryForObject("SELECT status FROM training_sessions WHERE id = ?::uuid", String.class, old))
                .isEqualTo("ABANDONED");
    }

    @Test
    void unAdminPuedeSaltearLaEvaluacionYUnUsuarioNo() throws Exception {
        auth(post("/api/v1/assessment/skip"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE username = ?", username);
        String adminToken = login(username);
        mockMvc.perform(post("/api/v1/assessment/skip").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForList("""
                SELECT p.level FROM performance_profiles p JOIN users u ON u.id = p.user_id
                WHERE u.username = ?""", Integer.class, username))
                .hasSize(3).containsOnly(1);
        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(jsonPath("$.assessmentCompleted").value(false));
    }

    private ResultActions attempt(String sessionId, String itemId, double accuracy) throws Exception {
        return auth(post("/api/v1/sessions/{id}/attempts", sessionId).contentType(MediaType.APPLICATION_JSON).content("""
                {"clientAttemptId": "%s", "sessionItemId": "%s", "isCorrect": %s, "accuracy": %s, "responseTimeMs": 1500}"""
                .formatted(UUID.randomUUID(), itemId, accuracy >= 0.5, accuracy)));
    }

    private void register(String user) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "%s", "password": "%s", "ageRange": "18-24"}""".formatted(user, PASSWORD)))
                .andExpect(status().isCreated());
    }

    private String login(String user) throws Exception {
        JsonNode body = json(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "%s"}""".formatted(user, PASSWORD))).andExpect(status().isOk()));
        return body.get("accessToken").asText();
    }

    private ResultActions auth(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static List<JsonNode> list(JsonNode array) {
        List<JsonNode> list = new java.util.ArrayList<>();
        array.forEach(list::add);
        return list;
    }
}
