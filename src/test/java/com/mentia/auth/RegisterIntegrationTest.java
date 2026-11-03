package com.mentia.auth;

import static com.mentia.support.Contract.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentia.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class RegisterIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "una-clave-larga";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registraUsuarioYPerfilDeEntrenamiento() throws Exception {
        String response = register("Lector.Nocturno", PASSWORD, "25-30")
                .andExpect(status().isCreated())
                .andExpect(matchesContract())
                .andReturn().getResponse().getContentAsString();
        UUID userId = UUID.fromString(objectMapper.readTree(response).get("userId").asText());

        Map<String, Object> user = jdbc.queryForMap("SELECT username, password_hash FROM users WHERE id = ?", userId);
        assertThat(user.get("username")).isEqualTo("lector.nocturno");
        String hash = (String) user.get("password_hash");
        assertThat(hash).isNotEqualTo(PASSWORD).doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();

        Map<String, Object> profile = jdbc.queryForMap(
                "SELECT age_range, session_minutes, onboarding_completed, goals::text AS goals"
                        + " FROM training_profiles WHERE user_id = ?", userId);
        assertThat(profile)
                .containsEntry("age_range", "25-30")
                .containsEntry("session_minutes", 10)
                .containsEntry("onboarding_completed", false)
                .containsEntry("goals", "[]");
    }

    @Test
    void usernameDuplicadoSinDistinguirMayusculasDevuelve409() throws Exception {
        register("duplicado_x", PASSWORD, "18-24").andExpect(status().isCreated());

        register("Duplicado_X", "otra-clave-larga", "31-40")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"))
                .andExpect(matchesContract());

        Integer count = jdbc.queryForObject("SELECT count(*) FROM users WHERE username = 'duplicado_x'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void propiedadDesconocidaSeRechazaYNoSeGuardaNada() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "con_dni", "password": "una-clave-larga", "ageRange": "18-24", "dni": "12345678"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        Integer count = jdbc.queryForObject("SELECT count(*) FROM users WHERE username = 'con_dni'", Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void contrasenaDebilDevuelve400() throws Exception {
        register("clave_corta", "corta", "18-24")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void endpointProtegidoSinTokenDevuelve401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private ResultActions register(String username, String password, String ageRange) throws Exception {
        JsonNode body = objectMapper.createObjectNode()
                .put("username", username)
                .put("password", password)
                .put("ageRange", ageRange);
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }
}
