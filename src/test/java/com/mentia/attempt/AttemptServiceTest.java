package com.mentia.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.mentia.attempt.domain.Attempt;
import com.mentia.attempt.dto.AttemptRequest;
import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;
import com.mentia.session.SessionService;
import com.mentia.session.dto.AttemptTarget;

@ExtendWith(MockitoExtension.class)
class AttemptServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final UUID SESSION = UUID.randomUUID();
    private static final UUID ITEM = UUID.randomUUID();
    private static final AttemptTarget TARGET = new AttemptTarget(SESSION, ITEM, UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), 4);

    @Mock
    AttemptRepository repository;

    @Mock
    SessionService sessionService;

    AttemptService service;

    @BeforeEach
    void setUp() {
        service = new AttemptService(repository, sessionService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registraUnIntentoNuevoConLosDatosDelItem() {
        AttemptRequest request = request(UUID.randomUUID(), ITEM);
        given(sessionService.attemptTarget(USER, SESSION, ITEM)).willReturn(TARGET);
        given(repository.saveAndFlush(any())).willAnswer(inv -> withId(inv.getArgument(0)));

        AttemptService.Recorded recorded = service.record(USER, SESSION, request);

        assertThat(recorded.created()).isTrue();
        assertThat(recorded.attempt().recordedAt()).isEqualTo(NOW);
        ArgumentCaptor<Attempt> saved = ArgumentCaptor.forClass(Attempt.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getExerciseId()).isEqualTo(TARGET.exerciseId());
        assertThat(saved.getValue().getVariantId()).isEqualTo(TARGET.variantId());
        assertThat(saved.getValue().getCategoryId()).isEqualTo(TARGET.categoryId());
        assertThat(saved.getValue().getDifficultyLevel()).isEqualTo(4);
        assertThat(saved.getValue().getAccuracy()).isEqualTo(0.75);
        assertThat(saved.getValue().getUserId()).isEqualTo(USER);
    }

    @Test
    void unReintentoDevuelveElOriginalSinValidarElEstadoDeLaSesion() {
        UUID clientId = UUID.randomUUID();
        Attempt original = withId(Attempt.record(USER, clientId, TARGET, null, true, 1.0, 900, null, NOW));
        given(repository.findByUserIdAndClientAttemptId(USER, clientId)).willReturn(Optional.of(original));

        AttemptService.Recorded recorded = service.record(USER, SESSION, request(clientId, ITEM));

        assertThat(recorded.created()).isFalse();
        assertThat(recorded.attempt().attemptId()).isEqualTo(original.getId());
        verify(sessionService, never()).attemptTarget(any(), any(), any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void reusarElClientAttemptIdParaOtroItemEsUnConflicto() {
        UUID clientId = UUID.randomUUID();
        Attempt original = withId(Attempt.record(USER, clientId, TARGET, null, true, 1.0, 900, null, NOW));
        given(repository.findByUserIdAndClientAttemptId(USER, clientId)).willReturn(Optional.of(original));

        expectConflict(() -> service.record(USER, SESSION, request(clientId, UUID.randomUUID())));
    }

    @Test
    void unItemConIntentoNoAceptaOtroDistinto() {
        given(sessionService.attemptTarget(USER, SESSION, ITEM)).willReturn(TARGET);
        given(repository.existsBySessionItemId(ITEM)).willReturn(true);

        expectConflict(() -> service.record(USER, SESSION, request(UUID.randomUUID(), ITEM)));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void siDosReintentosLleganALaVezElSegundoDevuelveElPrimero() {
        UUID clientId = UUID.randomUUID();
        Attempt winner = withId(Attempt.record(USER, clientId, TARGET, null, true, 1.0, 900, null, NOW));
        given(sessionService.attemptTarget(USER, SESSION, ITEM)).willReturn(TARGET);
        given(repository.findByUserIdAndClientAttemptId(USER, clientId))
                .willReturn(Optional.empty(), Optional.of(winner));
        given(repository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("uk_attempts_client_attempt"));

        AttemptService.Recorded recorded = service.record(USER, SESSION, request(clientId, ITEM));

        assertThat(recorded.created()).isFalse();
        assertThat(recorded.attempt().attemptId()).isEqualTo(winner.getId());
    }

    @Test
    void losErroresDeSesionSePropagan() {
        given(sessionService.attemptTarget(USER, SESSION, ITEM)).willThrow(new ApiException(HttpStatus.CONFLICT,
                ErrorCode.SESSION_STATE_INVALID, "No está en curso."));

        assertThatThrownBy(() -> service.record(USER, SESSION, request(UUID.randomUUID(), ITEM)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCode.SESSION_STATE_INVALID);
    }

    private void expectConflict(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCode.ATTEMPT_CONFLICT);
    }

    private static AttemptRequest request(UUID clientAttemptId, UUID itemId) {
        return new AttemptRequest(clientAttemptId, itemId, Map.of("answer", "ABC"), true, 0.75, 1200,
                Map.of("omissions", 0));
    }

    private static Attempt withId(Attempt attempt) {
        ReflectionTestUtils.setField(attempt, "id", UUID.randomUUID());
        return attempt;
    }
}
