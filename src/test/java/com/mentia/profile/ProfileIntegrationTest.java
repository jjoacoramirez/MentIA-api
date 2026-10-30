package com.mentia.profile;

import static com.mentia.support.Contract.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentia.security.JwtService;
import com.mentia.support.IntegrationTest;

@RecordApplicationEvents
class ProfileIntegrationTest extends IntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JwtService jwtService;

    @Autowired
    ApplicationEvents events;

    @Test
    void elPerfilSeCreaEnElRegistroYSeActualiza() throws Exception {
        String token = registerAndToken("perfil_" + System.nanoTime() % 100000);

        mockMvc.perform(auth(get("/api/v1/profile"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ageRange").value("18-24"))
                .andExpect(jsonPath("$.sessionMinutes").value(10))
                .andExpect(jsonPath("$.onboardingCompleted").value(false))
                .andExpect(matchesContract());

        mockMvc.perform(auth(put("/api/v1/profile"), token).contentType(MediaType.APPLICATION_JSON).content("""
                        {"educationLevel": "TERCIARIO", "sessionMinutes": 20,
                         "goals": ["MEMORIA_TRABAJO", "ATENCION"], "onboardingCompleted": true}"""))
                .andExpect(status().isOk())
                .andExpect(matchesContract());

        mockMvc.perform(auth(get("/api/v1/profile"), token))
                .andExpect(jsonPath("$.educationLevel").value("TERCIARIO"))
                .andExpect(jsonPath("$.sessionMinutes").value(20))
                .andExpect(jsonPath("$.goals[0]").value("MEMORIA_TRABAJO"))
                .andExpect(jsonPath("$.onboardingCompleted").value(true));

        assertThat(events.stream(OnboardingCompletedEvent.class)).hasSize(1);
    }

    @Test
    void unObjetivoInactivoSeRechaza() throws Exception {
        String token = registerAndToken("perfil_meta_" + System.nanoTime() % 100000);

        mockMvc.perform(auth(put("/api/v1/profile"), token).contentType(MediaType.APPLICATION_JSON).content("""
                        {"goals": ["MEMORIA_AUDITIVA"]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("goals"));
    }

    private String registerAndToken(String username) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "%s", "password": "una-clave-larga", "ageRange": "18-24"}""".formatted(username)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID userId = UUID.fromString(objectMapper.readTree(body).get("userId").asText());
        return jwtService.issue(userId).token();
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
