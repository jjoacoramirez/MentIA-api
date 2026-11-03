package com.mentia.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mentia.catalog.CatalogService;
import com.mentia.catalog.dto.CategoryRef;
import com.mentia.engine.EngineProperties;
import com.mentia.performance.AssessmentProperties.Threshold;
import com.mentia.performance.domain.PerformanceProfile;
import com.mentia.performance.dto.CategoryOutcome;
import com.mentia.performance.dto.PerformanceProfileResponse;

@ExtendWith(MockitoExtension.class)
class PerformanceServiceTest {

    private static final Instant BEFORE = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final UUID ATTENTION = UUID.randomUUID();
    private static final UUID VISUAL = UUID.randomUUID();
    private static final EngineProperties ENGINE = new EngineProperties(3, 1, 10, 0.3);

    @Mock
    PerformanceRepository repository;

    @Mock
    CatalogService catalogService;

    PerformanceService service;

    @BeforeEach
    void setUp() {
        InitialLevelPolicy policy = new InitialLevelPolicy(
                new AssessmentProperties(2, List.of(new Threshold(0.0, 1))), ENGINE);
        service = new PerformanceService(repository, policy, catalogService, ENGINE, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void elScoreSeActualizaConMediaMovilYElNivelEsElDecidido() {
        PerformanceProfile profile = new PerformanceProfile(USER, ATTENTION, 60.0, 3, 6, BEFORE);
        given(repository.findById(new PerformanceProfile.Key(USER, ATTENTION))).willReturn(Optional.of(profile));

        service.recordSession(USER, Map.of(ATTENTION, new CategoryOutcome(List.of(1.0, 0.8), 4)));

        // 60 × 0.7 + 90 × 0.3 = 69
        assertThat(profile.getScore()).isCloseTo(69.0, within(1e-9));
        assertThat(profile.getLevel()).isEqualTo(4);
        assertThat(profile.getSamples()).isEqualTo(8);
        assertThat(profile.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void elNivelSeAcotaAlRangoConfigurado() {
        PerformanceProfile profile = new PerformanceProfile(USER, ATTENTION, 90.0, 10, 6, BEFORE);
        given(repository.findById(new PerformanceProfile.Key(USER, ATTENTION))).willReturn(Optional.of(profile));

        service.recordSession(USER, Map.of(ATTENTION, new CategoryOutcome(List.of(1.0), 11)));

        assertThat(profile.getLevel()).isEqualTo(10);
    }

    @Test
    void unaCategoriaSinIntentosNoCambia() {
        service.recordSession(USER, Map.of(ATTENTION, new CategoryOutcome(List.of(), 4)));

        verify(repository, never()).findById(any());
        verify(repository, never()).save(any());
    }

    @Test
    void unaCategoriaSinPerfilArrancaConElScoreDeLaSesion() {
        given(repository.findById(new PerformanceProfile.Key(USER, VISUAL))).willReturn(Optional.empty());

        service.recordSession(USER, Map.of(VISUAL, new CategoryOutcome(List.of(0.5), 3)));

        ArgumentCaptor<PerformanceProfile> saved = ArgumentCaptor.forClass(PerformanceProfile.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getScore()).isEqualTo(50.0);
        assertThat(saved.getValue().getLevel()).isEqualTo(3);
        assertThat(saved.getValue().getSamples()).isEqualTo(1);
    }

    @Test
    void elPerfilSeDevuelveEnElOrdenDeLasCategoriasYRedondeado() {
        given(repository.findByIdUserId(USER)).willReturn(List.of(
                new PerformanceProfile(USER, VISUAL, 72.3456, 4, 2, BEFORE),
                new PerformanceProfile(USER, ATTENTION, 55.0, 3, 2, BEFORE)));
        given(catalogService.activeCategoryRefs()).willReturn(List.of(
                new CategoryRef(ATTENTION, "ATENCION"), new CategoryRef(VISUAL, "MEMORIA_VISUAL"),
                new CategoryRef(UUID.randomUUID(), "MEMORIA_TRABAJO")));

        PerformanceProfileResponse response = service.profile(USER);

        assertThat(response.categories()).containsExactly(
                new PerformanceProfileResponse.Entry("ATENCION", 55.0, 3, BEFORE),
                new PerformanceProfileResponse.Entry("MEMORIA_VISUAL", 72.3, 4, BEFORE));
    }
}
