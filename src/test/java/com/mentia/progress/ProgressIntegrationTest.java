package com.mentia.progress;

import static com.mentia.support.Contract.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.mentia.support.ApiClient;
import com.mentia.support.IntegrationTest;

class ProgressIntegrationTest extends IntegrationTest {

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired
    ApiClient api;

    ApiClient.User user;

    @BeforeEach
    void setUp() throws Exception {
        user = api.skipAssessment(api.newUser());
        completeSession(0.6);
        completeSession(1.0);
        String abandoned = api.json(api.perform(user, post("/api/v1/sessions")).andExpect(status().isCreated()))
                .get("id").asText();
        api.perform(user, post("/api/v1/sessions/{id}/abandon", abandoned)).andExpect(status().isNoContent());
    }

    @Test
    void elResumenCuentaSesionesDiasActivosYRacha() throws Exception {
        api.perform(user, get("/api/v1/progress/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionsCompleted").value(2))
                .andExpect(jsonPath("$.sessionsAbandoned").value(1))
                .andExpect(jsonPath("$.activeDaysLast30").value(1))
                .andExpect(jsonPath("$.currentStreakDays").value(1))
                .andExpect(jsonPath("$.lastSessionAt").isNotEmpty())
                .andExpect(jsonPath("$.dailyGoal").isEmpty())
                .andExpect(jsonPath("$.gamification.xp").isNumber())
                .andExpect(matchesContract());
    }

    @Test
    void elHistorialListaTodasLasSesionesPaginadas() throws Exception {
        JsonNode page = api.json(api.perform(user, get("/api/v1/sessions").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(matchesContract()));

        assertThat(page.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page.get("items")).hasSize(2);
        assertThat(page.get("items").get(0).get("status").asText()).as("la más reciente primero").isEqualTo("ABANDONED");
        assertThat(page.get("items").get(1).get("status").asText()).isEqualTo("COMPLETED");
        assertThat(page.get("items").get(1).get("accuracy").asDouble()).isEqualTo(1.0);
        assertThat(page.get("items").get(1).get("itemsCompleted").asInt()).isPositive();

        JsonNode second = api.json(api.perform(user, get("/api/v1/sessions").param("page", "1").param("size", "2")));
        assertThat(second.get("items")).hasSize(1);
    }

    @Test
    void laEvolucionPorCategoriaAgrupaPorSemana() throws Exception {
        JsonNode categories = api.json(api.perform(user, get("/api/v1/progress/categories"))
                .andExpect(status().isOk())
                .andExpect(matchesContract()));

        LocalDate weekStart = LocalDate.now(AR).with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        Set<String> codes = new HashSet<>();
        for (JsonNode category : categories) {
            codes.add(category.get("category").asText());
            JsonNode point = category.get("points").get(0);
            assertThat(point.get("periodStart").asText()).isEqualTo(weekStart.toString());
            assertThat(point.get("sessions").asInt()).isEqualTo(2);
            assertThat(point.get("accuracy").asDouble()).isBetween(0.6, 1.0);
        }
        assertThat(codes).containsExactlyInAnyOrder("ATENCION", "MEMORIA_VISUAL", "MEMORIA_TRABAJO");

        api.perform(user, get("/api/v1/progress/categories").param("from", "2026-12-01").param("to", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"));
    }

    private void completeSession(double accuracy) throws Exception {
        JsonNode session = api.json(api.perform(user, post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON).content("{\"durationMinutes\": 10}")).andExpect(status().isCreated()));
        String id = session.get("id").asText();
        api.perform(user, post("/api/v1/sessions/{id}/start", id)).andExpect(status().isOk());
        for (JsonNode item : session.get("items")) {
            api.attempt(user, id, item.get("sessionItemId").asText(), accuracy, 400).andExpect(status().isCreated());
        }
        api.perform(user, post("/api/v1/sessions/{id}/complete", id)).andExpect(status().isOk());
    }
}
