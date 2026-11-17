package com.mentia.metrics;

import static com.mentia.support.Contract.matchesContract;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mentia.security.Role;
import com.mentia.support.WebLayerTest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminMetricsController.class)
class AdminMetricsControllerTest extends WebLayerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdminMetricsService service;

    @Test
    void requiresAdmin() throws Exception {
        mvc.perform(get("/api/v1/admin/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/metrics").with(authenticatedAs(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanLoadReportAndInvalidFiltersAreRejected() throws Exception {
        var date = LocalDate.of(2026, 11, 4);
        given(service.report(7, "ATENCION", "")).willReturn(
                new AdminMetricsService.Report(date.minusDays(6), date, 0, List.of(), List.of(), List.of(), List.of()));
        mvc.perform(get("/api/v1/admin/metrics").param("days", "7").param("category", "ATENCION")
                .with(authenticatedAs(UUID.randomUUID(), Role.ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.participants").value(0)).andExpect(matchesContract());
        mvc.perform(get("/api/v1/admin/metrics").param("days", "365")
                .with(authenticatedAs(UUID.randomUUID(), Role.ADMIN)))
                .andExpect(status().isBadRequest());
    }
}
