package com.mentia.support;

import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.mentia.security.AuthenticatedUser;
import com.mentia.security.JwtService;
import com.mentia.security.SecurityConfig;
import com.mentia.security.TokenRevocationService;
import com.mentia.security.Role;

/**
 * Base para tests de controllers con {@code @WebMvcTest}: carga la cadena de seguridad real con el JWT simulado.
 * Uso: {@code mockMvc.perform(get(...).with(authenticatedAs(userId)))}.
 */
@Import(SecurityConfig.class)
public abstract class WebLayerTest {

    @MockitoBean
    protected JwtService jwtService;

    @MockitoBean
    protected TokenRevocationService revocationService;

    protected AuthenticatedUser principalFor(UUID userId) {
        return principalFor(userId, Role.USER);
    }

    protected AuthenticatedUser principalFor(UUID userId, Role role) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return new AuthenticatedUser(userId, "jti-" + userId, now, now.plus(1, ChronoUnit.DAYS), 0, role);
    }

    /** Agrega un Bearer que el filtro JWT acepta como el usuario indicado. */
    protected RequestPostProcessor authenticatedAs(UUID userId) {
        return authenticatedAs(userId, Role.USER);
    }

    protected RequestPostProcessor authenticatedAs(UUID userId, Role role) {
        AuthenticatedUser principal = principalFor(userId, role);
        String token = "token-" + userId;
        given(jwtService.parse(token)).willReturn(Optional.of(principal));
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }

    protected static MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
