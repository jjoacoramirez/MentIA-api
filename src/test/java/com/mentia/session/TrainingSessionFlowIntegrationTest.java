package com.mentia.session;

import static com.mentia.support.Contract.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.mentia.support.ApiClient;
import com.mentia.support.IntegrationTest;

class TrainingSessionFlowIntegrationTest extends IntegrationTest {

    @Autowired
    ApiClient api;

    ApiClient.User user;

    @BeforeEach
    void setUp() throws Exception {
        user = api.newUser();
    }

    @Test
    void sinPuntoDePartidaPideLaEvaluacion() throws Exception {
        api.perform(user, post("/api/v1/sessions"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSESSMENT_REQUIRED"))
                .andExpect(matchesContract());
    }

    @Test
    void generaUnaSesionQueLlenaElTiempoConTodasLasCategorias() throws Exception {
        api.skipAssessment(user);

        JsonNode session = api.json(api.perform(user, post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"durationMinutes\": 10}"))
                .andExpect(status().isCreated())
                .andExpect(matchesContract()));

        List<JsonNode> items = list(session.get("items"));
        assertThat(session.get("type").asText()).isEqualTo("TRAINING");
        assertThat(session.get("plannedMinutes").asInt()).isEqualTo(10);
        assertThat(session.get("estimatedDurationSeconds").asInt()).isLessThanOrEqualTo(600);
        assertThat(items).allSatisfy(i -> assertThat(i.get("difficultyLevel").asInt()).isEqualTo(1));
        Set<String> categories = new HashSet<>();
        items.forEach(i -> categories.add(i.get("category").asText()));
        assertThat(categories).containsExactlyInAnyOrder("ATENCION", "MEMORIA_VISUAL", "MEMORIA_TRABAJO");
        assertThat(list(session.get("rationale"))).last()
                .satisfies(r -> assertThat(r.get("code").asText()).isEqualTo("VARIETY_ROTATION"));

        // Pedirla otra vez la retoma, y se puede consultar por id; otro usuario no la ve.
        String id = session.get("id").asText();
        assertThat(api.json(api.perform(user, post("/api/v1/sessions")).andExpect(status().isOk())).get("id").asText())
                .isEqualTo(id);
        api.perform(user, get("/api/v1/sessions/{id}", id)).andExpect(status().isOk()).andExpect(matchesContract());
        api.perform(api.newUser(), get("/api/v1/sessions/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void alCerrarSubeElNivelDeLasCategoriasConBuenDesempenoYLoExplicaEnLaSiguiente() throws Exception {
        api.skipAssessment(user);
        JsonNode session = api.json(api.perform(user, post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"durationMinutes\": 30}"))
                .andExpect(status().isCreated()));
        String id = session.get("id").asText();
        api.perform(user, post("/api/v1/sessions/{id}/start", id)).andExpect(status().isOk());
        for (JsonNode item : list(session.get("items"))) {
            api.attempt(user, id, item.get("sessionItemId").asText(), 1.0, 300).andExpect(status().isCreated());
        }

        JsonNode result = api.json(api.perform(user, post("/api/v1/sessions/{id}/complete", id))
                .andExpect(status().isOk())
                .andExpect(matchesContract()));

        List<String> raised = new ArrayList<>();
        for (JsonNode category : list(result.get("categories"))) {
            int expected = category.get("itemsCompleted").asInt() >= 2 ? 2 : 1;
            assertThat(category.get("difficultyLevelAfter").asInt()).as(category.toString()).isEqualTo(expected);
            if (expected == 2) {
                raised.add(category.get("category").asText());
            }
        }
        assertThat(raised).isNotEmpty();
        assertThat(list(result.get("adaptations"))).hasSize(list(result.get("categories")).size());

        JsonNode next = api.json(api.perform(user, post("/api/v1/sessions")).andExpect(status().isCreated()));
        List<String> levelUps = list(next.get("rationale")).stream()
                .filter(r -> r.get("code").asText().equals("LEVEL_UP"))
                .map(r -> r.get("category").asText())
                .toList();
        assertThat(levelUps).containsExactlyInAnyOrderElementsOf(raised);
        assertThat(list(next.get("items"))).allSatisfy(i -> assertThat(i.get("difficultyLevel").asInt())
                .isEqualTo(raised.contains(i.get("category").asText()) ? 2 : 1));
    }

    @Test
    void elResultadoQuedaGuardadoYSeComparaConLaSesionAnterior() throws Exception {
        api.skipAssessment(user);
        JsonNode first = playAndComplete(0.5);
        assertThat(first.get("comparison").isNull()).as("sin sesión previa").isTrue();

        JsonNode second = playAndComplete(1.0);
        assertThat(second.get("comparison").get("previousAccuracy").asDouble()).isEqualTo(0.5);
        assertThat(second.get("comparison").get("accuracyDelta").asDouble()).isEqualTo(0.5);

        JsonNode stored = api.json(api.perform(user, get("/api/v1/sessions/{id}/result", second.get("sessionId").asText()))
                .andExpect(status().isOk())
                .andExpect(matchesContract()));
        assertThat(stored).isEqualTo(second);
    }

    @Test
    void abandonarCierraLaSesionSinTocarElDesempeno() throws Exception {
        api.skipAssessment(user);
        JsonNode session = api.json(api.perform(user, post("/api/v1/sessions")).andExpect(status().isCreated()));
        String id = session.get("id").asText();
        api.perform(user, post("/api/v1/sessions/{id}/start", id)).andExpect(status().isOk());
        String itemId = session.get("items").get(0).get("sessionItemId").asText();
        api.attempt(user, id, itemId, 1.0, 300).andExpect(status().isCreated());

        api.perform(user, get("/api/v1/sessions/{id}/result", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"));
        api.perform(user, post("/api/v1/sessions/{id}/abandon", id)).andExpect(status().isNoContent());

        api.perform(user, post("/api/v1/sessions/{id}/abandon", id)).andExpect(status().isConflict());
        api.attempt(user, id, session.get("items").get(1).get("sessionItemId").asText(), 1.0, 300)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"));
        api.perform(user, get("/api/v1/profile/performance"))
                .andExpect(jsonPath("$.categories[0].score").value(0.0))
                .andExpect(jsonPath("$.categories[0].level").value(1));
        api.perform(api.newUser(), post("/api/v1/sessions/{id}/abandon", id)).andExpect(status().isNotFound());

        // Una nueva pedida ya no retoma la abandonada.
        assertThat(api.json(api.perform(user, post("/api/v1/sessions")).andExpect(status().isCreated()))
                .get("id").asText()).isNotEqualTo(id);
    }

    private JsonNode playAndComplete(double accuracy) throws Exception {
        JsonNode session = api.json(api.perform(user, post("/api/v1/sessions")).andExpect(status().isCreated()));
        String id = session.get("id").asText();
        api.perform(user, post("/api/v1/sessions/{id}/start", id)).andExpect(status().isOk());
        for (JsonNode item : list(session.get("items"))) {
            api.attempt(user, id, item.get("sessionItemId").asText(), accuracy, 300).andExpect(status().isCreated());
        }
        return api.json(api.perform(user, post("/api/v1/sessions/{id}/complete", id))
                .andExpect(status().isOk())
                .andExpect(matchesContract()));
    }

    private static List<JsonNode> list(JsonNode array) {
        List<JsonNode> list = new ArrayList<>();
        array.forEach(list::add);
        return list;
    }
}
