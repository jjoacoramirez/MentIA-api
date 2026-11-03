package com.mentia.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import com.mentia.catalog.CatalogService;
import com.mentia.common.error.FieldValidationException;
import com.mentia.profile.domain.AgeRange;
import com.mentia.profile.domain.EducationLevel;
import com.mentia.profile.domain.TrainingProfile;
import com.mentia.profile.dto.ProfileResponse;
import com.mentia.profile.dto.ProfileUpdateRequest;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    private static final Instant CREATED = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final UUID USER = UUID.randomUUID();

    @Mock
    ProfileRepository repository;

    @Mock
    CatalogService catalogService;

    @Mock
    ObjectProvider<AssessmentCompletionQuery> assessmentCompletion;

    @Mock
    ApplicationEventPublisher events;

    ProfileService service;

    TrainingProfile profile;

    @BeforeEach
    void setUp() {
        service = new ProfileService(repository, catalogService, assessmentCompletion, events,
                Clock.fixed(NOW, ZoneOffset.UTC));
        profile = TrainingProfile.initial(USER, AgeRange.FROM_18_TO_24, CREATED);
        given(repository.findById(USER)).willReturn(Optional.of(profile));
    }

    @Test
    void elPerfilInicialTieneLosValoresPorDefecto() {
        ProfileResponse response = service.get(USER);

        assertThat(response).isEqualTo(new ProfileResponse(AgeRange.FROM_18_TO_24, null, 10, List.of(), false, false));
    }

    @Test
    void assessmentCompletedSaleDelModuloDeSesionesSiEstaDisponible() {
        given(assessmentCompletion.getIfAvailable()).willReturn(userId -> userId.equals(USER));

        assertThat(service.get(USER).assessmentCompleted()).isTrue();
    }

    @Test
    void soloCambiaLosCamposEnviados() {
        ProfileResponse response = service.update(USER, new ProfileUpdateRequest(null, null, 20, null, null));

        assertThat(response.sessionMinutes()).isEqualTo(20);
        assertThat(response.ageRange()).isEqualTo(AgeRange.FROM_18_TO_24);
        assertThat(profile.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void elNivelEducativoSePuedeCargarYBorrar() {
        service.update(USER, new ProfileUpdateRequest(null, Optional.of(EducationLevel.POSGRADO), null, null, null));
        assertThat(profile.getEducationLevel()).isEqualTo(EducationLevel.POSGRADO);

        service.update(USER, new ProfileUpdateRequest(null, null, null, null, null));
        assertThat(profile.getEducationLevel()).as("ausente no cambia").isEqualTo(EducationLevel.POSGRADO);

        service.update(USER, new ProfileUpdateRequest(null, Optional.empty(), null, null, null));
        assertThat(profile.getEducationLevel()).as("null explícito borra").isNull();
    }

    @Test
    void losObjetivosSeGuardanSinRepetidosYEnOrden() {
        given(catalogService.activeCategoryCodes()).willReturn(codes("ATENCION", "MEMORIA_VISUAL", "MEMORIA_TRABAJO"));

        ProfileResponse response = service.update(USER, new ProfileUpdateRequest(null, null, null,
                List.of("MEMORIA_TRABAJO", "ATENCION", "MEMORIA_TRABAJO"), null));

        assertThat(response.goals()).containsExactly("MEMORIA_TRABAJO", "ATENCION");
    }

    @Test
    void unObjetivoQueNoEsUnaCategoriaActivaSeRechaza() {
        given(catalogService.activeCategoryCodes()).willReturn(codes("ATENCION"));

        assertThatThrownBy(() -> service.update(USER,
                new ProfileUpdateRequest(null, null, null, List.of("ATENCION", "MEMORIA_AUDITIVA"), null)))
                .isInstanceOf(FieldValidationException.class)
                .extracting(e -> ((FieldValidationException) e).getField())
                .isEqualTo("goals");
        assertThat(profile.getGoals()).isEmpty();
    }

    @Test
    void elEventoDeOnboardingSePublicaSoloLaPrimeraVez() {
        service.update(USER, onboarding(true));
        service.update(USER, onboarding(false));
        service.update(USER, onboarding(true));

        verify(events, times(1)).publishEvent(new OnboardingCompletedEvent(USER, NOW));
        assertThat(profile.isOnboardingCompleted()).isTrue();
    }

    @Test
    void marcarOnboardingEnFalseNoPublicaNada() {
        service.update(USER, onboarding(false));

        verify(events, never()).publishEvent(any(Object.class));
    }

    private static ProfileUpdateRequest onboarding(boolean completed) {
        return new ProfileUpdateRequest(null, null, null, null, completed);
    }

    private static LinkedHashSet<String> codes(String... codes) {
        return new LinkedHashSet<>(List.of(codes));
    }
}
