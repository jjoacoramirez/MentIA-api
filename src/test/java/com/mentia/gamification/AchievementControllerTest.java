package com.mentia.gamification;

import static com.mentia.support.Contract.matchesContract;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mentia.gamification.dto.AchievementResponse;
import com.mentia.support.WebLayerTest;

@WebMvcTest(AchievementController.class)
class AchievementControllerTest extends WebLayerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AchievementService achievementService;

    @Test
    void listaLogrosObtenidosYPendientesConElContrato() throws Exception {
        UUID user = UUID.randomUUID();
        given(achievementService.list(user)).willReturn(List.of(
                new AchievementResponse("FIRST_SESSION", "Primer paso", "Completaste tu primera sesión en MentIA.", true,
                        Instant.parse("2026-11-03T12:00:00Z")),
                new AchievementResponse("FIVE_SESSIONS", "Constancia", "Completaste 5 sesiones.", false, null)));

        mockMvc.perform(get("/api/v1/achievements").with(authenticatedAs(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unlocked").value(true))
                .andExpect(jsonPath("$[1].unlockedAt").isEmpty())
                .andExpect(matchesContract());
    }

    @Test
    void requiereAutenticacion() throws Exception {
        mockMvc.perform(get("/api/v1/achievements")).andExpect(status().isUnauthorized());
    }
}
