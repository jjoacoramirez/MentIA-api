package com.mentia.session;

import static com.mentia.support.Contract.matchesContract;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;
import com.mentia.session.domain.SessionStatus;
import com.mentia.session.domain.SessionType;
import com.mentia.session.dto.SessionResponse;
import com.mentia.session.dto.SessionResultResponse;
import com.mentia.session.dto.SessionResultResponse.Adaptation;
import com.mentia.session.dto.SessionResultResponse.CategoryResult;
import com.mentia.support.WebLayerTest;

@WebMvcTest(SessionController.class)
class SessionControllerTest extends WebLayerTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID SESSION = UUID.randomUUID();

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SessionService sessionService;

    @MockitoBean
    SessionLifecycleService lifecycleService;

    @MockitoBean
    SessionGenerationService generationService;

    @Test
    void generarDevuelve201ConElContratoYUsaLaDuracionPedida() throws Exception {
        given(generationService.create(USER, 15)).willReturn(new SessionGenerationService.Generated(session(), true));

        mockMvc.perform(post("/api/v1/sessions").with(authenticatedAs(USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"durationMinutes\": 15}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rationale[0].code").value("VARIETY_ROTATION"))
                .andExpect(matchesContract());
    }

    @Test
    void sinBodyUsaLaDuracionDelPerfilYRetomarDevuelve200() throws Exception {
        given(generationService.create(USER, null)).willReturn(new SessionGenerationService.Generated(session(), false));

        mockMvc.perform(post("/api/v1/sessions").with(authenticatedAs(USER)))
                .andExpect(status().isOk())
                .andExpect(matchesContract());
    }

    @Test
    void duracionFueraDeRangoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/sessions").with(authenticatedAs(USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"durationMinutes\": 45}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("durationMinutes"));
    }

    @Test
    void sinEvaluacionDevuelve409AssessmentRequired() throws Exception {
        given(generationService.create(USER, null)).willThrow(new ApiException(HttpStatus.CONFLICT,
                ErrorCode.ASSESSMENT_REQUIRED, "Antes de entrenar necesitás completar la evaluación inicial."));

        mockMvc.perform(post("/api/v1/sessions").with(authenticatedAs(USER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSESSMENT_REQUIRED"))
                .andExpect(matchesContract());
    }

    @Test
    void obtenerUnaSesionConElContrato() throws Exception {
        given(sessionService.get(USER, SESSION)).willReturn(session());

        mockMvc.perform(get("/api/v1/sessions/{id}", SESSION).with(authenticatedAs(USER)))
                .andExpect(status().isOk())
                .andExpect(matchesContract());
    }

    private static SessionResponse session() {
        Map<String, Object> rationale = new java.util.LinkedHashMap<>();
        rationale.put("code", "VARIETY_ROTATION");
        rationale.put("category", null);
        rationale.put("message", "Rotamos ejercicios y estímulos para que cada sesión sea distinta.");
        return new SessionResponse(SESSION, SessionType.TRAINING, SessionStatus.GENERATED, 15, 840, List.of(),
                List.of(rationale), Instant.parse("2026-11-03T12:00:00Z"));
    }

    @Test
    void iniciarDevuelveLaSesionEnCursoConElContrato() throws Exception {
        given(sessionService.start(USER, SESSION)).willReturn(new SessionResponse(SESSION, SessionType.TRAINING,
                SessionStatus.IN_PROGRESS, 10, 0, List.of(), List.of(), Instant.parse("2026-11-02T12:00:00Z")));

        mockMvc.perform(post("/api/v1/sessions/{id}/start", SESSION).with(authenticatedAs(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(matchesContract());
    }

    @Test
    void iniciarUnaSesionYaIniciadaDevuelve409ConElContrato() throws Exception {
        given(sessionService.start(USER, SESSION)).willThrow(new ApiException(HttpStatus.CONFLICT,
                ErrorCode.SESSION_STATE_INVALID, "Esta sesión ya fue iniciada o está cerrada."));

        mockMvc.perform(post("/api/v1/sessions/{id}/start", SESSION).with(authenticatedAs(USER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"))
                .andExpect(matchesContract());
    }

    @Test
    void cerrarDevuelveElResultadoConElContrato() throws Exception {
        given(lifecycleService.complete(USER, SESSION)).willReturn(new SessionResultResponse(SESSION,
                SessionType.INITIAL_ASSESSMENT, SessionStatus.COMPLETED, 6, 6, 5, 1, 0.83, 9000L,
                List.of(new CategoryResult("ATENCION", 2, 0.9, 1100, 3, 5)),
                List.of(Adaptation.between("ATENCION", 3, 5)), null, null));

        mockMvc.perform(post("/api/v1/sessions/{id}/complete", SESSION).with(authenticatedAs(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].difficultyLevelAfter").value(5))
                .andExpect(jsonPath("$.adaptations[0].direction").value("UP"))
                .andExpect(matchesContract());
    }

    @Test
    void cerrarUnaSesionAjenaDevuelve404ConElContrato() throws Exception {
        given(lifecycleService.complete(USER, SESSION)).willThrow(new ApiException(HttpStatus.NOT_FOUND,
                ErrorCode.SESSION_NOT_FOUND, "No encontramos esa sesión."));

        mockMvc.perform(post("/api/v1/sessions/{id}/complete", SESSION).with(authenticatedAs(USER)))
                .andExpect(status().isNotFound())
                .andExpect(matchesContract());
    }
}
