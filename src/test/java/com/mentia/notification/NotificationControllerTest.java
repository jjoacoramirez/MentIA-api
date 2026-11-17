package com.mentia.notification;

import static com.mentia.support.Contract.matchesContract;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.mentia.common.error.FieldValidationException;
import com.mentia.notification.dto.PushConfigResponse;
import com.mentia.support.WebLayerTest;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest extends WebLayerTest {

    @Autowired MockMvc mvc;
    @MockitoBean NotificationService service;

    private static String body(String endpoint) {
        return "{\"endpoint\": \"" + endpoint + "\"}";
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/notifications/push-config")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/notifications/push-subscription").contentType(MediaType.APPLICATION_JSON)
                .content(body(PushTestKeys.ENDPOINT))).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsThePublicConfigAndNeverTheSecret() throws Exception {
        given(service.config()).willReturn(new PushConfigResponse(true, PushTestKeys.PUBLIC));
        mvc.perform(get("/api/v1/notifications/push-config").with(authenticatedAs(UUID.randomUUID())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.publicKey").value(PushTestKeys.PUBLIC))
                .andExpect(matchesContract());
    }

    @Test
    void subscribesAndUnsubscribesTheCurrentUser() throws Exception {
        UUID user = UUID.randomUUID();
        mvc.perform(put("/api/v1/notifications/push-subscription").with(authenticatedAs(user))
                .contentType(MediaType.APPLICATION_JSON).content(body(PushTestKeys.ENDPOINT)))
                .andExpect(status().isNoContent()).andExpect(matchesContract());
        verify(service).subscribe(user, PushTestKeys.ENDPOINT);

        mvc.perform(delete("/api/v1/notifications/push-subscription").with(authenticatedAs(user))
                .contentType(MediaType.APPLICATION_JSON).content(body(PushTestKeys.ENDPOINT)))
                .andExpect(status().isNoContent()).andExpect(matchesContract());
        verify(service).unsubscribe(user, PushTestKeys.ENDPOINT);
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        UUID user = UUID.randomUUID();
        willThrow(new FieldValidationException("endpoint", "La suscripción no es válida"))
                .given(service).subscribe(user, "https://evil.example/x");
        mvc.perform(put("/api/v1/notifications/push-subscription").with(authenticatedAs(user))
                .contentType(MediaType.APPLICATION_JSON).content(body("https://evil.example/x")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(put("/api/v1/notifications/push-subscription").with(authenticatedAs(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\": \"\"}"))
                .andExpect(status().isBadRequest());
        // Jackson rechaza propiedades desconocidas: no se acepta nada extra "por las dudas".
        mvc.perform(put("/api/v1/notifications/push-subscription").with(authenticatedAs(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\": \"https://fcm.googleapis.com/x\", \"email\": \"a@b.c\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void answers503WhenRemindersAreOff() throws Exception {
        UUID user = UUID.randomUUID();
        willThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.PUSH_DISABLED, "No disponible"))
                .given(service).subscribe(user, PushTestKeys.ENDPOINT);
        mvc.perform(put("/api/v1/notifications/push-subscription").with(authenticatedAs(user))
                .contentType(MediaType.APPLICATION_JSON).content(body(PushTestKeys.ENDPOINT)))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PUSH_DISABLED"))
                .andExpect(matchesContract());
    }
}
