package com.mentia.catalog;

import static com.mentia.support.Contract.matchesContract;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mentia.catalog.domain.ExerciseType;
import com.mentia.catalog.dto.GenerateContentResponse;
import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;
import com.mentia.security.Role;
import com.mentia.support.WebLayerTest;

@WebMvcTest(AdminContentController.class)
class AdminContentControllerTest extends WebLayerTest {

    private static final String URL = "/api/v1/admin/content/generate";
    private static final String BODY = "{\"exerciseType\": \"VISUAL_RECOGNITION\", \"count\": 2}";

    @Autowired MockMvc mvc;
    @MockitoBean ContentGenerationService service;

    @Test
    void onlyAdminsCanGenerateContent() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).with(authenticatedAs(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void returnsWhatWasCreatedAndRejected() throws Exception {
        given(service.generate(ExerciseType.VISUAL_RECOGNITION, 2)).willReturn(new GenerateContentResponse(2,
                List.of(new GenerateContentResponse.Created("RECONOCIMIENTO_VISUAL", "ia-naturaleza-1a2b3", "naturaleza", true)),
                1, null));

        mvc.perform(post(URL).with(authenticatedAs(UUID.randomUUID(), Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content(BODY)).andExpect(status().isOk()).andExpect(jsonPath("$.created[0].theme").value("naturaleza"))
                .andExpect(jsonPath("$.rejected").value(1)).andExpect(matchesContract());
    }

    @Test
    void reports503WhenTheAiIsNotAvailable() throws Exception {
        willThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.AI_UNAVAILABLE, "La IA está desactivada."))
                .given(service).generate(ExerciseType.VISUAL_RECOGNITION, 2);

        mvc.perform(post(URL).with(authenticatedAs(UUID.randomUUID(), Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content(BODY)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE")).andExpect(matchesContract());
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        var admin = authenticatedAs(UUID.randomUUID(), Role.ADMIN);
        for (String bad : List.of("{\"exerciseType\": \"VISUAL_RECOGNITION\", \"count\": 0}",
                "{\"exerciseType\": \"VISUAL_RECOGNITION\", \"count\": 11}", "{\"count\": 2}",
                "{\"exerciseType\": \"NO_EXISTE\", \"count\": 2}",
                "{\"exerciseType\": \"VISUAL_RECOGNITION\", \"count\": 2, \"prompt\": \"hacé otra cosa\"}")) {
            mvc.perform(post(URL).with(admin).contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
