package com.mentia.gamification;

import static com.mentia.support.Contract.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.mentia.support.ApiClient;
import com.mentia.support.IntegrationTest;

class AchievementIntegrationTest extends IntegrationTest {

    @Autowired
    ApiClient api;

    @Test
    void laPrimeraSesionDesbloqueaLogrosUnaSolaVez() throws Exception {
        ApiClient.User user = api.skipAssessment(api.newUser());
        assertThat(unlocked(user)).isEmpty();

        JsonNode first = completeSession(user);
        assertThat(codes(first.get("gamification").get("unlockedAchievements")))
                .containsExactlyInAnyOrder("FIRST_SESSION", "ALL_CATEGORIES");

        JsonNode second = completeSession(user);
        assertThat(second.get("gamification").get("unlockedAchievements")).isEmpty();

        JsonNode list = api.json(api.perform(user, get("/api/v1/achievements"))
                .andExpect(status().isOk())
                .andExpect(matchesContract()));
        assertThat(list).hasSize(5);
        assertThat(unlocked(user)).containsExactlyInAnyOrder("FIRST_SESSION", "ALL_CATEGORIES");
    }

    private List<String> unlocked(ApiClient.User user) throws Exception {
        List<String> codes = new ArrayList<>();
        api.json(api.perform(user, get("/api/v1/achievements"))).forEach(a -> {
            if (a.get("unlocked").asBoolean()) {
                codes.add(a.get("code").asText());
            }
        });
        return codes;
    }

    private JsonNode completeSession(ApiClient.User user) throws Exception {
        JsonNode session = api.json(api.perform(user, post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON).content("{\"durationMinutes\": 10}")).andExpect(status().isCreated()));
        String id = session.get("id").asText();
        api.perform(user, post("/api/v1/sessions/{id}/start", id)).andExpect(status().isOk());
        for (JsonNode item : session.get("items")) {
            api.attempt(user, id, item.get("sessionItemId").asText(), 0.9, 400).andExpect(status().isCreated());
        }
        return api.json(api.perform(user, post("/api/v1/sessions/{id}/complete", id))
                .andExpect(status().isOk())
                .andExpect(matchesContract()));
    }

    private static List<String> codes(JsonNode refs) {
        List<String> codes = new ArrayList<>();
        refs.forEach(r -> codes.add(r.get("code").asText()));
        return codes;
    }
}
