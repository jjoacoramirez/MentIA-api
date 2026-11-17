package com.mentia.notification;

import static com.mentia.support.Contract.matchesContract;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.mentia.notification.dto.TestPushResponse;
import com.mentia.security.Role;
import com.mentia.support.WebLayerTest;

@WebMvcTest(AdminNotificationController.class)
class AdminNotificationControllerTest extends WebLayerTest {

    private static final String URL = "/api/v1/admin/notifications/test";

    @Autowired MockMvc mvc;
    @MockitoBean PushTestService service;

    private static String body(String username) {
        return "{\"username\": \"" + username + "\"}";
    }

    @Test
    void onlyAdminsCanSendATestPush() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("ana")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(URL).with(authenticatedAs(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON)
                .content(body("ana"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void returnsOnlyCounts() throws Exception {
        given(service.send("ana")).willReturn(new TestPushResponse(2, 1, 1, 0));
        mvc.perform(post(URL).with(authenticatedAs(UUID.randomUUID(), Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(body("ana")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.devices").value(2))
                .andExpect(jsonPath("$.sent").value(1)).andExpect(jsonPath("$.endpoint").doesNotExist())
                .andExpect(matchesContract());
        verify(service).send("ana");
    }

    @Test
    void unknownAliasAnd503AreReportedWithStableCodes() throws Exception {
        willThrow(new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "No existe"))
                .given(service).send("nadie");
        mvc.perform(post(URL).with(authenticatedAs(UUID.randomUUID(), Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(body("nadie")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(matchesContract());

        willThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.PUSH_DISABLED, "Apagado"))
                .given(service).send("ana");
        mvc.perform(post(URL).with(authenticatedAs(UUID.randomUUID(), Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(body("ana")))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PUSH_DISABLED"));
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        var admin = authenticatedAs(UUID.randomUUID(), Role.ADMIN);
        mvc.perform(post(URL).with(admin).contentType(MediaType.APPLICATION_JSON).content(body("")))
                .andExpect(status().isBadRequest());
        mvc.perform(post(URL).with(admin).contentType(MediaType.APPLICATION_JSON).content(body("a b; DROP")))
                .andExpect(status().isBadRequest());
        mvc.perform(post(URL).with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"ana\", \"email\": \"a@b.c\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
