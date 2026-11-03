package com.mentia.gamification;

import static com.mentia.support.Contract.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.mentia.session.SessionCompletedEvent;
import com.mentia.session.domain.SessionType;
import com.mentia.support.ApiClient;
import com.mentia.support.IntegrationTest;

class GamificationIntegrationTest extends IntegrationTest {

    @Autowired
    ApiClient api;

    @Autowired
    GamificationService gamification;

    @Test
    void completarUnaSesionSumaXpYLoMuestraEnElResultado() throws Exception {
        ApiClient.User user = api.skipAssessment(api.newUser());
        api.perform(user, get("/api/v1/gamification/me"))
                .andExpect(jsonPath("$.xp").value(0))
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(matchesContract());

        JsonNode session = api.json(api.perform(user, post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON).content("{\"durationMinutes\": 5}")).andExpect(status().isCreated()));
        String id = session.get("id").asText();
        api.perform(user, post("/api/v1/sessions/{id}/start", id)).andExpect(status().isOk());
        int items = session.get("items").size();
        for (JsonNode item : session.get("items")) {
            api.attempt(user, id, item.get("sessionItemId").asText(), 1.0, 300).andExpect(status().isCreated());
        }

        JsonNode result = api.json(api.perform(user, post("/api/v1/sessions/{id}/complete", id))
                .andExpect(status().isOk())
                .andExpect(matchesContract()));

        int expectedXp = 10 + 2 * items + 5;
        assertThat(result.get("gamification").get("xpEarned").asInt()).isEqualTo(expectedXp);
        assertThat(result.get("gamification").get("xpTotal").asInt()).isEqualTo(expectedXp);
        assertThat(result.get("gamification").get("unlockedAchievements").isArray()).isTrue();
        api.perform(user, get("/api/v1/gamification/me")).andExpect(jsonPath("$.xp").value(expectedXp));
        api.perform(user, get("/api/v1/sessions/{id}/result", id))
                .andExpect(jsonPath("$.gamification.xpEarned").value(expectedXp));

        // El mismo evento no otorga XP dos veces.
        gamification.onSessionCompleted(new SessionCompletedEvent(user.id(), java.util.UUID.fromString(id),
                SessionType.TRAINING, items, 1.0, Set.of(), Instant.now()));
        assertThat(gamification.state(user.id()).xp()).isEqualTo(expectedXp);
    }
}
