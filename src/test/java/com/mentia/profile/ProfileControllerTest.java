package com.mentia.profile;

import static com.mentia.support.Contract.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.mentia.common.error.FieldValidationException;
import com.mentia.profile.domain.AgeRange;
import com.mentia.profile.domain.EducationLevel;
import com.mentia.profile.dto.ProfileResponse;
import com.mentia.profile.dto.ProfileUpdateRequest;
import com.mentia.support.WebLayerTest;

@WebMvcTest(ProfileController.class)
class ProfileControllerTest extends WebLayerTest {

    private static final UUID USER = UUID.randomUUID();
    private static final ProfileResponse PROFILE = new ProfileResponse(
            AgeRange.FROM_25_TO_30, EducationLevel.UNIVERSITARIO_EN_CURSO, 15, List.of("ATENCION"), true, false);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProfileService profileService;

    @Test
    void devuelveElPerfilDelUsuarioAutenticadoConElContrato() throws Exception {
        given(profileService.get(USER)).willReturn(PROFILE);

        mockMvc.perform(get("/api/v1/profile").with(authenticatedAs(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ageRange").value("25-30"))
                .andExpect(jsonPath("$.educationLevel").value("UNIVERSITARIO_EN_CURSO"))
                .andExpect(jsonPath("$.goals[0]").value("ATENCION"))
                .andExpect(jsonPath("$.assessmentCompleted").value(false))
                .andExpect(matchesContract());
    }

    @Test
    void actualizaYDevuelveElPerfilConElContrato() throws Exception {
        given(profileService.update(eq(USER), any())).willReturn(PROFILE);

        update("""
                {"ageRange": "25-30", "sessionMinutes": 15, "goals": ["ATENCION"], "onboardingCompleted": true}""")
                .andExpect(status().isOk())
                .andExpect(matchesContract());

        verify(profileService).update(USER,
                new ProfileUpdateRequest(AgeRange.FROM_25_TO_30, null, 15, List.of("ATENCION"), true));
    }

    @Test
    void distingueNivelEducativoAusenteDeNullExplicito() throws Exception {
        given(profileService.update(eq(USER), any())).willReturn(PROFILE);
        ArgumentCaptor<ProfileUpdateRequest> captor = ArgumentCaptor.forClass(ProfileUpdateRequest.class);

        update("{\"sessionMinutes\": 10}").andExpect(status().isOk());
        update("{\"educationLevel\": null}").andExpect(status().isOk());
        update("{\"educationLevel\": \"POSGRADO\"}").andExpect(status().isOk());

        verify(profileService, times(3)).update(eq(USER), captor.capture());
        assertThat(captor.getAllValues()).extracting(ProfileUpdateRequest::educationLevel)
                .containsExactly(null, Optional.empty(), Optional.of(EducationLevel.POSGRADO));
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 31})
    void duracionDeSesionFueraDeRangoDevuelve400(int minutes) throws Exception {
        expectValidationError("{\"sessionMinutes\": " + minutes + "}", "sessionMinutes");
    }

    @Test
    void masDeCuatroObjetivosDevuelve400() throws Exception {
        expectValidationError("""
                {"goals": ["A", "B", "C", "D", "E"]}""", "goals");
    }

    @Test
    void propiedadDesconocidaSeRechaza() throws Exception {
        expectValidationError("""
                {"assessmentCompleted": true}""", "assessmentCompleted");
    }

    @Test
    void nivelEducativoInvalidoDevuelve400() throws Exception {
        expectValidationError("""
                {"educationLevel": "DOCTORADO"}""", "educationLevel");
    }

    @Test
    void objetivoDesconocidoDevuelve400ConElCampo() throws Exception {
        given(profileService.update(eq(USER), any()))
                .willThrow(new FieldValidationException("goals", "Objetivo desconocido: OTRA"));

        update("""
                {"goals": ["OTRA"]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("goals"));
    }

    @Test
    void requiereAutenticacion() throws Exception {
        mockMvc.perform(get("/api/v1/profile")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private void expectValidationError(String body, String field) throws Exception {
        update(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value(field));
        verify(profileService, never()).update(any(), any());
    }

    private ResultActions update(String body) throws Exception {
        return mockMvc.perform(put("/api/v1/profile").with(authenticatedAs(USER))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
