package com.mentia.auth;

import static com.mentia.support.Contract.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentia.security.TokenRevocationService;
import com.mentia.support.IntegrationTest;

class AuthFlowIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "una-clave-larga";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TokenRevocationService revocationService;

    @Test
    void loginAccesoYLogoutRevocaElToken() throws Exception {
        register("flujo_completo");

        String token = accessToken(login("Flujo_Completo", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(matchesContract()));

        logout(token).andExpect(status().isNoContent()).andExpect(matchesContract());
        logout(token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void credencialesInvalidasDanSiempreElMismoError() throws Exception {
        register("con_cuenta");

        String wrongPassword = login("con_cuenta", "otra-clave-larga")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(matchesContract())
                .andReturn().getResponse().getContentAsString();
        String unknownUser = login("sin_cuenta", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(wrongPassword).get("detail"))
                .isEqualTo(objectMapper.readTree(unknownUser).get("detail"));
    }

    @Test
    void laLimpiezaBorraSoloLosTokensVencidos() {
        revocationService.revoke("vencido", Instant.now().minusSeconds(60));
        revocationService.revoke("vigente", Instant.now().plusSeconds(3600));

        revocationService.purgeExpired();

        assertThat(revocationService.isRevoked("vencido")).isFalse();
        assertThat(revocationService.isRevoked("vigente")).isTrue();
    }

    private void register(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "%s", "password": "%s", "ageRange": "18-24"}""".formatted(username, PASSWORD)))
                .andExpect(status().isCreated());
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "password": "%s"}""".formatted(username, password)));
    }

    private ResultActions logout(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String accessToken(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }
}
