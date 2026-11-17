package com.mentia.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.ai.AiPurpose;
import com.mentia.ai.AiRequest;
import com.mentia.ai.AiResponse;
import com.mentia.ai.AiUnavailableException;
import com.mentia.ai.GuardedAiService;
import com.mentia.support.ApiClient;
import com.mentia.support.IntegrationTest;

@Transactional
class FeedbackIntegrationTest extends IntegrationTest {

    private static final String RESULT = """
            {"type": "TRAINING", "itemsTotal": 6, "itemsCompleted": 6, "accuracy": 0.9,
             "categories": [{"category": "ATENCION", "itemsCompleted": 6, "accuracy": 0.9,
                             "difficultyLevelBefore": 3, "difficultyLevelAfter": 4}]}""";

    @Autowired ApiClient api;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @MockitoBean GuardedAiService ai;

    @BeforeEach
    void aiOffByDefault() {
        given(ai.generate(any(AiPurpose.class), any(AiRequest.class), any(UUID.class)))
                .willThrow(new AiUnavailableException(AiUnavailableException.Reason.DISABLED, "off"));
    }

    private UUID session(UUID user, String status, String result) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO training_sessions(id, user_id, type, status, planned_minutes, created_at, started_at, finished_at, result)
                VALUES (?, ?, 'TRAINING', ?, 5, ?, ?, ?, ?::jsonb)
                """, id, user, status, now, now, now, result);
        return id;
    }

    /** El test corre en una sola transacción: se vuelcan los cambios de JPA antes de consultar con JDBC. */
    private int feedbackRows(UUID session) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT count(*) FROM session_feedback WHERE session_id = ?", Integer.class, session);
    }

    @Test
    void withTheAiOffTheBasicTextIsStoredAndReturnedAgainWithoutRegenerating() throws Exception {
        var user = api.newUser();
        UUID session = session(user.id(), "COMPLETED", RESULT);

        api.perform(user, post("/api/v1/sessions/{id}/feedback", session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("BASIC")).andExpect(jsonPath("$.text").isNotEmpty());
        api.perform(user, post("/api/v1/sessions/{id}/feedback", session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("BASIC"));

        assertThat(feedbackRows(session)).isEqualTo(1);
    }

    @Test
    void anAiTextIsStoredOnceAndTheSecondOpenDoesNotCallTheAiAgain() throws Exception {
        var user = api.newUser();
        UUID session = session(user.id(), "COMPLETED", RESULT);
        given(ai.generate(any(AiPurpose.class), any(AiRequest.class), any(UUID.class))).willReturn(
                new AiResponse("¡Qué buena sesión! Subiste de nivel en Atención con muy buena precisión.", 90, 40));

        api.perform(user, post("/api/v1/sessions/{id}/feedback", session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("AI"));
        api.perform(user, post("/api/v1/sessions/{id}/feedback", session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("¡Qué buena sesión! Subiste de nivel en Atención con muy buena precisión."));

        verify(ai, times(1)).generate(any(AiPurpose.class), any(AiRequest.class), any(UUID.class));
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT source FROM session_feedback WHERE session_id = ?", String.class, session))
                .isEqualTo("AI");
    }

    @Test
    void aSessionThatIsNotYoursOrNotCompletedHasNoFeedback() throws Exception {
        var owner = api.newUser();
        var other = api.newUser();
        UUID done = session(owner.id(), "COMPLETED", RESULT);
        UUID open = session(owner.id(), "IN_PROGRESS", null);

        api.perform(other, post("/api/v1/sessions/{id}/feedback", done)).andExpect(status().isNotFound());
        api.perform(owner, post("/api/v1/sessions/{id}/feedback", open)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"));
        api.perform(owner, post("/api/v1/sessions/{id}/feedback", UUID.randomUUID())).andExpect(status().isNotFound());

        assertThat(feedbackRows(done)).isZero();
        assertThat(feedbackRows(open)).isZero();
    }

    @Test
    void theFeedbackDisappearsWithItsSession() throws Exception {
        var user = api.newUser();
        UUID session = session(user.id(), "COMPLETED", RESULT);
        api.perform(user, post("/api/v1/sessions/{id}/feedback", session)).andExpect(status().isOk());

        assertThat(feedbackRows(session)).isEqualTo(1);
        // Se borra la sesión (por ejemplo, al borrar la cuenta): el comentario se va con ella.
        jdbc.update("DELETE FROM training_sessions WHERE id = ?", session);
        assertThat(feedbackRows(session)).isZero();
    }
}
