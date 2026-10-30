package com.mentia.auth;

import static com.mentia.support.Contract.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentia.support.IntegrationTest;

class RecoveryIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "una-clave-larga";
    private static final String NEW_PASSWORD = "otra-clave-larga";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void elResetInvalidaLosTokensYRotaElCodigo() throws Exception {
        String code = json(register("recupera_todo")).get("recoveryCode").asText();
        assertThat(jdbc.queryForObject("SELECT recovery_code_hash FROM users WHERE username = 'recupera_todo'", String.class))
                .as("solo se guarda el hash").isNotEqualTo(code).doesNotContain(code);
        String oldToken = token("recupera_todo", PASSWORD);

        String newCode = json(reset("recupera_todo", code)
                .andExpect(status().isOk())
                .andExpect(matchesContract())).get("recoveryCode").asText();

        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        login("recupera_todo", PASSWORD).andExpect(status().isUnauthorized());
        String newToken = token("recupera_todo", NEW_PASSWORD);
        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken))
                .andExpect(status().isOk());

        reset("recupera_todo", code).andExpect(jsonPath("$.code").value("INVALID_RECOVERY"));
        assertThat(newCode).isNotEqualTo(code);
    }

    @Test
    void elIntentoFallidoQuedaRegistradoAunqueLaRespuestaSeaError() throws Exception {
        register("intento_fallido");

        reset("intento_fallido", "XXXXX-XXXXX-XXXXX-XXXXX")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RECOVERY"))
                .andExpect(matchesContract());

        assertThat(jdbc.queryForObject(
                "SELECT recovery_failed_attempts FROM users WHERE username = 'intento_fallido'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void regenerarCambiaElCodigoVigente() throws Exception {
        String code = json(register("regenera")).get("recoveryCode").asText();
        String token = token("regenera", PASSWORD);

        String regenerated = json(mockMvc.perform(post("/api/v1/auth/recovery/regenerate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(matchesContract())).get("recoveryCode").asText();

        reset("regenera", code).andExpect(status().isBadRequest());
        reset("regenera", regenerated).andExpect(status().isOk());
    }

    private ResultActions register(String username) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "%s", "password": "%s", "ageRange": "18-24"}""".formatted(username, PASSWORD)))
                .andExpect(status().isCreated());
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "password": "%s"}""".formatted(username, password)));
    }

    private String token(String username, String password) throws Exception {
        return json(login(username, password).andExpect(status().isOk())).get("accessToken").asText();
    }

    private ResultActions reset(String username, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/recovery/reset").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "recoveryCode": "%s", "newPassword": "%s"}""".formatted(username, code, NEW_PASSWORD)));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
