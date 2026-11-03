package com.mentia.performance;

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

import com.mentia.performance.dto.PerformanceProfileResponse;
import com.mentia.support.WebLayerTest;

@WebMvcTest(PerformanceController.class)
class PerformanceControllerTest extends WebLayerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PerformanceService performanceService;

    @Test
    void devuelveElDesempenoPorCategoriaConElContrato() throws Exception {
        UUID user = UUID.randomUUID();
        given(performanceService.profile(user)).willReturn(new PerformanceProfileResponse(List.of(
                new PerformanceProfileResponse.Entry("ATENCION", 72.5, 4, Instant.parse("2026-11-03T10:00:00Z")))));

        mockMvc.perform(get("/api/v1/profile/performance").with(authenticatedAs(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].category").value("ATENCION"))
                .andExpect(jsonPath("$.categories[0].score").value(72.5))
                .andExpect(jsonPath("$.categories[0].level").value(4))
                .andExpect(matchesContract());
    }

    @Test
    void requiereAutenticacion() throws Exception {
        mockMvc.perform(get("/api/v1/profile/performance")).andExpect(status().isUnauthorized());
    }
}
