package com.mentia.auth;

import static com.mentia.support.Contract.matchesContract;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mentia.auth.dto.RegisterRequest;
import com.mentia.auth.dto.RegisterResponse;
import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;
import com.mentia.profile.domain.AgeRange;
import com.mentia.auth.dto.LoginRequest;
import com.mentia.auth.dto.TokenResponse;
import com.mentia.security.AuthenticatedUser;
import com.mentia.support.WebLayerTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(AuthController.class)
class AuthControllerTest extends WebLayerTest {

    private static final String URL = "/api/v1/auth/register";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void registraYDevuelve201ConElContrato() throws Exception {
        UUID userId = UUID.randomUUID();
        given(authService.register(any())).willReturn(new RegisterResponse(userId, null));

        register("""
                {"username": "lector_nocturno", "password": "una-clave-larga", "ageRange": "18-24"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.recoveryCode").value(nullValue()))
                .andExpect(matchesContract());

        verify(authService).register(new RegisterRequest("lector_nocturno", "una-clave-larga", AgeRange.FROM_18_TO_24));
    }

    @Test
    void usernameOcupadoDevuelve409ConCodigoEstable() throws Exception {
        given(authService.register(any()))
                .willThrow(new ApiException(HttpStatus.CONFLICT, ErrorCode.USERNAME_TAKEN, "Ese nombre de usuario ya está en uso."));

        register("""
                {"username": "Repetido", "password": "una-clave-larga", "ageRange": "25-30"}""")
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(matchesContract());
    }

    @ParameterizedTest
    @ValueSource(strings = {"corta", "123456789"})
    void contrasenaDebilDevuelve400(String password) throws Exception {
        expectValidationError("""
                {"username": "usuario", "password": "%s", "ageRange": "18-24"}""".formatted(password), "password");
    }

    @Test
    void contrasenaDeMasDe72CaracteresDevuelve400() throws Exception {
        expectValidationError("""
                {"username": "usuario", "password": "%s", "ageRange": "18-24"}""".formatted("a".repeat(73)), "password");
    }

    @Test
    void contrasenaDeMasDe72BytesDevuelve400AunqueTengaMenosDe72Caracteres() throws Exception {
        // 40 caracteres de 2 bytes = 80 bytes: BCrypt ignoraría todo lo que pase de 72.
        expectValidationError("""
                {"username": "usuario", "password": "%s", "ageRange": "18-24"}""".formatted("ñ".repeat(40)), "password");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "con espacio", "tilde_á", "guion-medio", "a234567890123456789012345678901"})
    void usernameInvalidoDevuelve400(String username) throws Exception {
        expectValidationError("""
                {"username": "%s", "password": "una-clave-larga", "ageRange": "18-24"}""".formatted(username), "username");
    }

    @Test
    void propiedadDesconocidaSeRechaza() throws Exception {
        expectValidationError("""
                {"username": "usuario", "password": "una-clave-larga", "ageRange": "18-24", "email": "x@y.com"}""",
                "email");
    }

    @Test
    void rangoEtarioInvalidoDevuelve400() throws Exception {
        expectValidationError("""
                {"username": "usuario", "password": "una-clave-larga", "ageRange": "23"}""", "ageRange");
    }

    @Test
    void camposObligatoriosFaltantesDevuelven400() throws Exception {
        register("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(3));
        verify(authService, never()).register(any());
    }

    @Test
    void jsonMalformadoDevuelve400() throws Exception {
        register("{\"username\": ")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void laRespuestaDeErrorNoIncluyeLaContrasena() throws Exception {
        register("""
                {"username": "usuario", "password": "corta-77", "ageRange": "18-24"}""")
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("corta-77"))));
    }

    @Test
    void loginDevuelveElTokenConElContrato() throws Exception {
        given(authService.login(any())).willReturn(TokenResponse.bearer("un.jwt.firmado", 86400));

        login("""
                {"username": "Lector_Nocturno", "password": "una-clave-larga"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("un.jwt.firmado"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(86400))
                .andExpect(matchesContract());

        verify(authService).login(new LoginRequest("Lector_Nocturno", "una-clave-larga"));
    }

    @Test
    void loginConCredencialesInvalidasDevuelve401() throws Exception {
        given(authService.login(any())).willThrow(new ApiException(HttpStatus.UNAUTHORIZED,
                ErrorCode.INVALID_CREDENTIALS, "El nombre de usuario o la contraseña no son correctos."));

        login("""
                {"username": "alguien", "password": "otra-clave"}""")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(matchesContract());
    }

    @Test
    void loginSinCamposDevuelve400() throws Exception {
        login("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(2));
        verify(authService, never()).login(any());
    }

    @Test
    void logoutRevocaElTokenActual() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/auth/logout").with(authenticatedAs(userId)))
                .andExpect(status().isNoContent())
                .andExpect(matchesContract());

        verify(authService).logout(argThat(
                (AuthenticatedUser u) -> u.userId().equals(userId) && u.jti().equals("jti-" + userId)));
    }

    @Test
    void logoutSinTokenDevuelve401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(authService, never()).logout(any());
    }

    private ResultActions login(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void expectValidationError(String body, String field) throws Exception {
        register(body)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value(field));
        verify(authService, never()).register(any());
    }

    private ResultActions register(String body) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
