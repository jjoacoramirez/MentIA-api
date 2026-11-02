package com.mentia.attempt;

import static com.mentia.support.Contract.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentia.catalog.CatalogService;
import com.mentia.catalog.VariantSelector;
import com.mentia.catalog.dto.ExerciseDefinition;
import com.mentia.security.JwtService;
import com.mentia.session.SessionFactory;
import com.mentia.session.SessionRepository;
import com.mentia.session.domain.SessionItem;
import com.mentia.session.domain.SessionType;
import com.mentia.session.domain.TrainingSession;
import com.mentia.support.IntegrationTest;

class AttemptIntegrationTest extends IntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtService jwtService;

    @Autowired
    SessionFactory sessionFactory;

    @Autowired
    SessionRepository sessions;

    @Autowired
    CatalogService catalog;

    @Autowired
    VariantSelector variants;

    @Autowired
    TransactionTemplate tx;

    UUID user;
    String token;

    @BeforeEach
    void setUp() {
        user = newUser();
        token = jwtService.issue(user, 0).token();
    }

    @Test
    void registraElIntentoConLosDatosDelItemYUnReintentoDevuelveElOriginal() throws Exception {
        TrainingSession session = startedSession(user);
        SessionItem item = session.getItems().getFirst();
        UUID clientId = UUID.randomUUID();

        String first = record(session.getId(), clientId, item.getId(), token)
                .andExpect(status().isCreated())
                .andExpect(matchesContract())
                .andReturn().getResponse().getContentAsString();
        String retry = record(session.getId(), clientId, item.getId(), token)
                .andExpect(status().isOk())
                .andExpect(matchesContract())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(retry).get("attemptId")).isEqualTo(objectMapper.readTree(first).get("attemptId"));
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT exercise_id, variant_id, category_id, difficulty_level, is_correct, accuracy, response_time_ms,
                       response ->> 'answer' AS answer, metrics ->> 'omissions' AS omissions
                FROM attempts WHERE client_attempt_id = ?""", clientId);
        assertThat(row.get("exercise_id")).isEqualTo(item.getExerciseId());
        assertThat(row.get("variant_id")).isEqualTo(item.getVariantId());
        assertThat(row.get("category_id")).isEqualTo(item.getCategoryId());
        assertThat(((Number) row.get("difficulty_level")).intValue()).isEqualTo(3);
        assertThat(row).containsEntry("is_correct", true).containsEntry("accuracy", 0.75)
                .containsEntry("response_time_ms", 1200).containsEntry("answer", "ABC").containsEntry("omissions", "0");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM attempts WHERE session_item_id = ?", Integer.class, item.getId()))
                .isEqualTo(1);
    }

    @Test
    void unSegundoIntentoDistintoSobreElMismoItemEsUnConflicto() throws Exception {
        TrainingSession session = startedSession(user);
        UUID itemId = session.getItems().getFirst().getId();
        record(session.getId(), UUID.randomUUID(), itemId, token).andExpect(status().isCreated());

        record(session.getId(), UUID.randomUUID(), itemId, token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTEMPT_CONFLICT"));
    }

    @Test
    void unItemDeOtraSesionSeRechaza() throws Exception {
        TrainingSession session = startedSession(user);
        TrainingSession other = startedSession(user);

        record(session.getId(), UUID.randomUUID(), other.getItems().getFirst().getId(), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_IN_SESSION"))
                .andExpect(matchesContract());
    }

    @Test
    void unaSesionQueNoEstaEnCursoNoAceptaIntentos() throws Exception {
        TrainingSession generated = generatedSession(user);

        record(generated.getId(), UUID.randomUUID(), generated.getItems().getFirst().getId(), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"))
                .andExpect(matchesContract());
    }

    @Test
    void laSesionDeOtroUsuarioDevuelve404() throws Exception {
        TrainingSession foreign = startedSession(newUser());

        record(foreign.getId(), UUID.randomUUID(), foreign.getItems().getFirst().getId(), token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"))
                .andExpect(matchesContract());
        record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), token)
                .andExpect(status().isNotFound());
    }

    private ResultActions record(UUID sessionId, UUID clientId, UUID itemId, String bearer) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/{id}/attempts", sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"clientAttemptId": "%s", "sessionItemId": "%s", "response": {"answer": "ABC"},
                         "isCorrect": true, "accuracy": 0.75, "responseTimeMs": 1200, "metrics": {"omissions": 0}}"""
                        .formatted(clientId, itemId)));
    }

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, username, password_hash, created_at) VALUES (?, ?, 'x', ?)",
                id, "u" + id.toString().substring(0, 8), java.sql.Timestamp.from(Instant.now()));
        return id;
    }

    private TrainingSession startedSession(UUID owner) {
        return tx.execute(status -> {
            TrainingSession session = create(owner);
            session.start(Instant.now());
            return sessions.saveAndFlush(session);
        });
    }

    private TrainingSession generatedSession(UUID owner) {
        return tx.execute(status -> create(owner));
    }

    private TrainingSession create(UUID owner) {
        List<SessionFactory.PlannedItem> items = catalog.activeExercises("MEMORIA_TRABAJO").stream()
                .map(e -> planned(owner, e))
                .toList();
        TrainingSession session = sessionFactory.create(owner, SessionType.TRAINING, 10, items, List.of());
        session.getItems().size();
        return session;
    }

    private SessionFactory.PlannedItem planned(UUID owner, ExerciseDefinition exercise) {
        UUID categoryId = jdbc.queryForObject("SELECT id FROM categories WHERE code = ?", UUID.class,
                exercise.categoryCode());
        return new SessionFactory.PlannedItem(exercise.id(), variants.pick(owner, exercise.id(), 3).id(), categoryId,
                3, 42L, Map.of("sequenceLength", 5));
    }
}
