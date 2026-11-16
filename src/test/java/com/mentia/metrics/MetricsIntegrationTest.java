package com.mentia.metrics;

import static com.mentia.support.Contract.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.mentia.support.ApiClient;
import com.mentia.support.IntegrationTest;

class MetricsIntegrationTest extends IntegrationTest {

    @Autowired
    ApiClient api;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void soloUnAdminVeElReporte() throws Exception {
        api.perform(api.newUser(), get("/api/v1/admin/metrics/consistency"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(matchesContract());
    }

    @Test
    void unUsuarioConDosSesionesElMismoDiaSumaAlGrupoDeUnDiaSinExponerDatosIndividuales() throws Exception {
        ApiClient.User viewer = admin();
        JsonNode before = report(viewer);

        // El helper lo hace admin para saltear la evaluación; vuelve a USER para que cuente en las métricas.
        ApiClient.User trainee = api.skipAssessment(api.newUser());
        jdbc.update("UPDATE users SET role = 'USER' WHERE id = ?", trainee.id());
        playAndComplete(trainee, 0.5);
        playAndComplete(trainee, 1.0);

        JsonNode after = report(viewer);
        assertThat(after.get("totals").get("users").asInt()).isEqualTo(before.get("totals").get("users").asInt() + 1);
        assertThat(after.get("totals").get("sessionsCompleted").asInt())
                .isEqualTo(before.get("totals").get("sessionsCompleted").asInt() + 2);
        JsonNode oneDayBefore = before.get("groups").get(1);
        JsonNode oneDayAfter = after.get("groups").get(1);
        assertThat(oneDayAfter.get("label").asText()).isEqualTo("1 día");
        assertThat(oneDayAfter.get("users").asInt()).isEqualTo(oneDayBefore.get("users").asInt() + 1);
        assertThat(oneDayAfter.get("usersWithTrend").asInt()).isEqualTo(oneDayBefore.get("usersWithTrend").asInt() + 1);
        assertThat(after.toString()).doesNotContain(trainee.username()).doesNotContain(trainee.id().toString());

        // Las cuentas admin no cuentan.
        assertThat(after.get("totals").get("users").asInt())
                .isEqualTo(jdbc.queryForObject("SELECT count(*) FROM users WHERE role = 'USER'", Integer.class));
    }

    private ApiClient.User admin() throws Exception {
        ApiClient.User user = api.newUser();
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", user.id());
        return new ApiClient.User(user.username(), user.id(), api.login(user.username()));
    }

    private JsonNode report(ApiClient.User viewer) throws Exception {
        return api.json(api.perform(viewer, get("/api/v1/admin/metrics/consistency"))
                .andExpect(status().isOk())
                .andExpect(matchesContract()));
    }

    private void playAndComplete(ApiClient.User user, double accuracy) throws Exception {
        JsonNode session = api.json(api.perform(user, post("/api/v1/sessions")).andExpect(status().isCreated()));
        String id = session.get("id").asText();
        api.perform(user, post("/api/v1/sessions/{id}/start", id)).andExpect(status().isOk());
        for (JsonNode item : session.get("items")) {
            api.attempt(user, id, item.get("sessionItemId").asText(), accuracy, 300).andExpect(status().isCreated());
        }
        api.perform(user, post("/api/v1/sessions/{id}/complete", id)).andExpect(status().isOk());
    }
}
