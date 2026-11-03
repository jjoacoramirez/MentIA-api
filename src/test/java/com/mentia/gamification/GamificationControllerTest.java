package com.mentia.gamification;

import static com.mentia.support.Contract.matchesContract;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mentia.gamification.dto.GamificationState;
import com.mentia.support.WebLayerTest;

@WebMvcTest(GamificationController.class)
class GamificationControllerTest extends WebLayerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GamificationService gamificationService;

    @Test
    void devuelveXpYNivelConElContrato() throws Exception {
        UUID user = UUID.randomUUID();
        given(gamificationService.state(user)).willReturn(new GamificationState(150, 2, 50, 200));

        mockMvc.perform(get("/api/v1/gamification/me").with(authenticatedAs(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xp").value(150))
                .andExpect(jsonPath("$.level").value(2))
                .andExpect(jsonPath("$.xpIntoLevel").value(50))
                .andExpect(jsonPath("$.xpForNextLevel").value(200))
                .andExpect(matchesContract());
    }

    @Test
    void requiereAutenticacion() throws Exception {
        mockMvc.perform(get("/api/v1/gamification/me")).andExpect(status().isUnauthorized());
    }
}
