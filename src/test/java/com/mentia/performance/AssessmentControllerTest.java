package com.mentia.performance;

import static com.mentia.support.Contract.matchesContract;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mentia.catalog.domain.ExerciseType;
import com.mentia.catalog.domain.Modality;
import com.mentia.security.Role;
import com.mentia.session.domain.SessionStatus;
import com.mentia.session.domain.SessionType;
import com.mentia.session.dto.SessionResponse;
import com.mentia.support.WebLayerTest;

@WebMvcTest(AssessmentController.class)
class AssessmentControllerTest extends WebLayerTest {

    private static final UUID USER = UUID.randomUUID();

    static final SessionResponse SESSION = new SessionResponse(UUID.randomUUID(), SessionType.INITIAL_ASSESSMENT,
            SessionStatus.GENERATED, 6, 330,
            List.of(new SessionResponse.Item(UUID.randomUUID(), 1, "N_BACK", ExerciseType.N_BACK, "MEMORIA_TRABAJO",
                    Modality.VISUAL, 3, "letras", 123456789L, Map.of("n", 1, "trials", 20),
                    Map.of("symbols", List.of("B", "C")), "Tocá el botón cuando el elemento se repita.")),
            List.of(), Instant.parse("2026-11-02T12:00:00Z"));

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AssessmentService assessmentService;

    @Test
    void crearLaEvaluacionDevuelve201ConElContrato() throws Exception {
        given(assessmentService.start(USER, false)).willReturn(new AssessmentService.Started(SESSION, true));

        mockMvc.perform(post("/api/v1/assessment/start").with(authenticatedAs(USER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("INITIAL_ASSESSMENT"))
                .andExpect(jsonPath("$.items[0].exerciseType").value("N_BACK"))
                .andExpect(jsonPath("$.items[0].content.symbols[0]").value("B"))
                .andExpect(jsonPath("$.rationale").isEmpty())
                .andExpect(matchesContract());
    }

    @Test
    void retomarDevuelve200YRestartSePasaAlServicio() throws Exception {
        given(assessmentService.start(USER, true)).willReturn(new AssessmentService.Started(SESSION, false));

        mockMvc.perform(post("/api/v1/assessment/start").param("restart", "true").with(authenticatedAs(USER)))
                .andExpect(status().isOk())
                .andExpect(matchesContract());
    }

    @Test
    void restartInvalidoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/assessment/start").param("restart", "quizas").with(authenticatedAs(USER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void saltearEsSoloParaAdmins() throws Exception {
        mockMvc.perform(post("/api/v1/assessment/skip").with(authenticatedAs(USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(matchesContract());
        verify(assessmentService, never()).skip(any());

        UUID admin = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/assessment/skip").with(authenticatedAs(admin, Role.ADMIN)))
                .andExpect(status().isNoContent())
                .andExpect(matchesContract());
        verify(assessmentService).skip(admin);
    }

    @Test
    void requiereAutenticacion() throws Exception {
        mockMvc.perform(post("/api/v1/assessment/start")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/assessment/skip")).andExpect(status().isUnauthorized());
    }
}
