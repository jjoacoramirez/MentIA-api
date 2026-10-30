package com.mentia.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mentia.auth.AuthController;
import com.mentia.auth.AuthService;
import com.mentia.support.WebLayerTest;

@WebMvcTest(AuthController.class)
class SecurityConfigTest extends WebLayerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void endpointProtegidoSinTokenDevuelve401ComoProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    void tokenValidoAutentica() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").with(authenticatedAs(UUID.randomUUID())))
                .andExpect(status().isNoContent());
    }

    @Test
    void tokenInvalidoOVencidoDevuelve401() throws Exception {
        given(jwtService.parse(anyString())).willReturn(Optional.empty());

        mockMvc.perform(withBearer(post("/api/v1/auth/logout"), "cualquier-cosa"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void tokenRevocadoDevuelve401() throws Exception {
        UUID userId = UUID.randomUUID();
        var request = post("/api/v1/auth/logout").with(authenticatedAs(userId));
        given(revocationService.isRevoked("jti-" + userId)).willReturn(true);

        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void esquemaQueNoEsBearerSeIgnora() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }
}
