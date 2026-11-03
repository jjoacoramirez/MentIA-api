package com.mentia.progress;

import static com.mentia.support.Contract.matchesContract;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mentia.gamification.dto.GamificationState;
import com.mentia.progress.dto.CategoryProgress;
import com.mentia.progress.dto.ProgressSummary;
import com.mentia.progress.dto.SessionHistoryPage;
import com.mentia.support.WebLayerTest;

@WebMvcTest(ProgressController.class)
class ProgressControllerTest extends WebLayerTest {

    private static final UUID USER = UUID.randomUUID();

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProgressService progressService;

    @Test
    void historialPaginadoConElContrato() throws Exception {
        given(progressService.sessions(USER, 1, 10)).willReturn(new SessionHistoryPage(List.of(
                new SessionHistoryPage.Summary(UUID.randomUUID(), "TRAINING", "COMPLETED",
                        Instant.parse("2026-11-03T12:00:00Z"), 0.8, 6, 9000),
                new SessionHistoryPage.Summary(UUID.randomUUID(), "TRAINING", "GENERATED", null, null, 0, 0)),
                1, 10, 12));

        mockMvc.perform(get("/api/v1/sessions").param("page", "1").param("size", "10").with(authenticatedAs(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.items[1].accuracy").isEmpty())
                .andExpect(matchesContract());
    }

    @ParameterizedTest
    @CsvSource({
            "page, -1",
            "size, 0",
            "size, 51"})
    void paginacionFueraDeRangoDevuelve400(String param, String value) throws Exception {
        mockMvc.perform(get("/api/v1/sessions").param(param, value).with(authenticatedAs(USER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void resumenConElContrato() throws Exception {
        given(progressService.summary(USER)).willReturn(new ProgressSummary(5, 1, 4,
                Instant.parse("2026-11-03T12:00:00Z"), 2, null, new GamificationState(150, 2, 50, 200)));

        mockMvc.perform(get("/api/v1/progress/summary").with(authenticatedAs(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreakDays").value(2))
                .andExpect(jsonPath("$.dailyGoal").isEmpty())
                .andExpect(jsonPath("$.gamification.level").value(2))
                .andExpect(matchesContract());
    }

    @Test
    void evolucionPorCategoriaConElContrato() throws Exception {
        given(progressService.categories(eq(USER), eq("month"), any(), any())).willReturn(List.of(
                new CategoryProgress("ATENCION", List.of(
                        new CategoryProgress.Point(LocalDate.of(2026, 10, 1), 0.7, 3.5, 4),
                        new CategoryProgress.Point(LocalDate.of(2026, 11, 1), 0.85, 4.0, 2)))));

        mockMvc.perform(get("/api/v1/progress/categories").param("granularity", "month")
                        .param("from", "2026-09-01").param("to", "2026-11-30").with(authenticatedAs(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].points[0].periodStart").value("2026-10-01"))
                .andExpect(matchesContract());
    }

    @ParameterizedTest
    @CsvSource({
            "granularity, day",
            "from, 03-11-2026"})
    void parametrosInvalidosDevuelven400(String param, String value) throws Exception {
        mockMvc.perform(get("/api/v1/progress/categories").param(param, value).with(authenticatedAs(USER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void requiereAutenticacion() throws Exception {
        mockMvc.perform(get("/api/v1/sessions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/progress/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/progress/categories")).andExpect(status().isUnauthorized());
    }
}
