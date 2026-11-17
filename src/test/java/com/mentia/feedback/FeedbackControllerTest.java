package com.mentia.feedback;

import static com.mentia.support.Contract.matchesContract;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;
import com.mentia.feedback.dto.FeedbackResponse;
import com.mentia.support.WebLayerTest;

@WebMvcTest(FeedbackController.class)
class FeedbackControllerTest extends WebLayerTest {

    @Autowired MockMvc mvc;
    @MockitoBean FeedbackService service;

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/sessions/{id}/feedback", UUID.randomUUID())).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void returnsTheFeedbackOfTheCurrentUsersSession() throws Exception {
        UUID user = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        given(service.getOrCreate(user, session)).willReturn(new FeedbackResponse("Buena sesión, seguí así.", FeedbackSource.AI));

        mvc.perform(post("/api/v1/sessions/{id}/feedback", session).with(authenticatedAs(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.text").value("Buena sesión, seguí así."))
                .andExpect(jsonPath("$.source").value("AI")).andExpect(matchesContract());
    }

    @Test
    void notFoundAndNotCompletedKeepTheirStableCodes() throws Exception {
        UUID user = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        UUID open = UUID.randomUUID();
        given(service.getOrCreate(user, missing))
                .willThrow(new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SESSION_NOT_FOUND, "No existe"));
        given(service.getOrCreate(user, open))
                .willThrow(new ApiException(HttpStatus.CONFLICT, ErrorCode.SESSION_STATE_INVALID, "No está completada"));

        mvc.perform(post("/api/v1/sessions/{id}/feedback", missing).with(authenticatedAs(user)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"))
                .andExpect(matchesContract());
        mvc.perform(post("/api/v1/sessions/{id}/feedback", open).with(authenticatedAs(user)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"))
                .andExpect(matchesContract());
    }
}
