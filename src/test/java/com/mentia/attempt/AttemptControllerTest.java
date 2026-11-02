package com.mentia.attempt;

import static com.mentia.support.Contract.matchesContract;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.mentia.attempt.dto.AttemptRequest;
import com.mentia.attempt.dto.AttemptResponse;
import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;
import com.mentia.support.WebLayerTest;

@WebMvcTest(AttemptController.class)
class AttemptControllerTest extends WebLayerTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID SESSION = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.fromString("0b4f7f0e-8a43-4c55-9d2e-6b8b2c3f1a10");
    private static final UUID ITEM = UUID.fromString("5a1d2c3b-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
    private static final AttemptResponse ATTEMPT = new AttemptResponse(UUID.randomUUID(),
            Instant.parse("2026-11-02T12:00:00Z"));

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AttemptService attemptService;

    @Test
    void elPrimerRegistroDevuelve201ConElContrato() throws Exception {
        given(attemptService.record(eq(USER), eq(SESSION), any())).willReturn(new AttemptService.Recorded(ATTEMPT, true));

        record(validBody())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptId").value(ATTEMPT.attemptId().toString()))
                .andExpect(matchesContract());

        verify(attemptService).record(USER, SESSION, new AttemptRequest(CLIENT_ID, ITEM,
                Map.of("sequence", java.util.List.of("A", "B")), true, 0.8, 1500, Map.of("maxLength", 4)));
    }

    @Test
    void unReintentoDevuelve200ConElContrato() throws Exception {
        given(attemptService.record(eq(USER), eq(SESSION), any())).willReturn(new AttemptService.Recorded(ATTEMPT, false));

        record(validBody()).andExpect(status().isOk()).andExpect(matchesContract());
    }

    @Test
    void responseYMetricsSonOpcionales() throws Exception {
        given(attemptService.record(eq(USER), eq(SESSION), any())).willReturn(new AttemptService.Recorded(ATTEMPT, true));

        record("""
                {"clientAttemptId": "%s", "sessionItemId": "%s", "isCorrect": false, "accuracy": 0, "responseTimeMs": 0}"""
                .formatted(CLIENT_ID, ITEM))
                .andExpect(status().isCreated())
                .andExpect(matchesContract());
    }

    @ParameterizedTest
    @CsvSource({
            "accuracy, 1.2",
            "accuracy, -0.1",
            "responseTimeMs, -1"})
    void valoresFueraDeRangoDevuelven400(String field, String value) throws Exception {
        String body = validBody().replaceFirst("\"" + field + "\": [^,}]+", "\"" + field + "\": " + value);
        record(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value(field));
        verify(attemptService, never()).record(any(), any(), any());
    }

    @Test
    void faltanCamposObligatorios() throws Exception {
        record("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(5));
    }

    @Test
    void propiedadDesconocidaSeRechaza() throws Exception {
        record(validBody().replace("\"metrics\"", "\"userId\": \"x\", \"metrics\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("userId"));
    }

    @Test
    void sesionAjenaDevuelve404ConElContrato() throws Exception {
        given(attemptService.record(eq(USER), eq(SESSION), any())).willThrow(new ApiException(HttpStatus.NOT_FOUND,
                ErrorCode.SESSION_NOT_FOUND, "No encontramos esa sesión."));

        record(validBody())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"))
                .andExpect(matchesContract());
    }

    @Test
    void conflictoDevuelve409ConElContrato() throws Exception {
        given(attemptService.record(eq(USER), eq(SESSION), any())).willThrow(new ApiException(HttpStatus.CONFLICT,
                ErrorCode.ATTEMPT_CONFLICT, "Ese ejercicio ya tiene un resultado registrado."));

        record(validBody())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTEMPT_CONFLICT"))
                .andExpect(matchesContract());
    }

    @Test
    void requiereAutenticacion() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/{id}/attempts", SESSION)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isUnauthorized());
    }

    private String validBody() {
        return """
                {"clientAttemptId": "%s", "sessionItemId": "%s", "response": {"sequence": ["A", "B"]},
                 "isCorrect": true, "accuracy": 0.8, "responseTimeMs": 1500, "metrics": {"maxLength": 4}}"""
                .formatted(CLIENT_ID, ITEM);
    }

    private ResultActions record(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/{id}/attempts", SESSION).with(authenticatedAs(USER))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
