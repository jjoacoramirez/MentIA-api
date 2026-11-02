package com.mentia.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mentia.session.domain.PlannedItem;
import com.mentia.session.domain.SessionItem;
import com.mentia.session.domain.SessionStatus;
import com.mentia.session.domain.SessionType;
import com.mentia.session.domain.TrainingSession;

class TrainingSessionTest {

    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");

    @Test
    void losItemsSeNumeranEnOrdenDesdeUno() {
        TrainingSession session = newSession();

        SessionItem first = session.addItem(item());
        SessionItem second = session.addItem(item());

        assertThat(first.getPosition()).isEqualTo(1);
        assertThat(second.getPosition()).isEqualTo(2);
        assertThat(first.getEstimatedSeconds()).isEqualTo(60);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.GENERATED);
    }

    @Test
    void soloSeIniciaUnaSesionGenerada() {
        TrainingSession session = newSession();

        session.start(NOW);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(session.getStartedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> session.start(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> session.addItem(item()))
                .as("una sesión iniciada ya no cambia sus ítems")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void soloSeCompletaUnaSesionEnCursoYGuardaElResultado() {
        TrainingSession session = newSession();
        assertThatThrownBy(() -> session.complete(NOW, Map.of())).isInstanceOf(IllegalStateException.class);

        session.start(NOW);
        session.complete(NOW.plusSeconds(300), Map.of("accuracy", 0.8));

        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.getFinishedAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(session.getResult()).containsEntry("accuracy", 0.8);
        assertThat(session.isOpen()).isFalse();
    }

    @Test
    void soloSeAbandonaUnaSesionAbierta() {
        TrainingSession generated = newSession();
        generated.abandon(NOW);
        assertThat(generated.getStatus()).isEqualTo(SessionStatus.ABANDONED);
        assertThatThrownBy(() -> generated.abandon(NOW)).isInstanceOf(IllegalStateException.class);

        TrainingSession inProgress = newSession();
        inProgress.start(NOW);
        inProgress.abandon(NOW);
        assertThat(inProgress.getStatus()).isEqualTo(SessionStatus.ABANDONED);
    }

    @Test
    void unaSesionAbiertaVenceDespuesDelPlazo() {
        TrainingSession session = newSession();
        Duration staleAfter = Duration.ofHours(12);

        assertThat(session.isStale(NOW.plus(Duration.ofHours(11)), staleAfter)).isFalse();
        assertThat(session.isStale(NOW.plus(Duration.ofHours(13)), staleAfter)).isTrue();
        session.abandon(NOW);
        assertThat(session.isStale(NOW.plus(Duration.ofHours(13)), staleAfter)).as("cerrada no vence").isFalse();
    }

    @Test
    void sabeDeQuienEs() {
        UUID owner = UUID.randomUUID();
        TrainingSession session = TrainingSession.generate(owner, SessionType.INITIAL_ASSESSMENT, 10, List.of(), NOW);

        assertThat(session.isOwnedBy(owner)).isTrue();
        assertThat(session.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    private static TrainingSession newSession() {
        return TrainingSession.generate(UUID.randomUUID(), SessionType.TRAINING, 10, List.of(), NOW);
    }

    private static PlannedItem item() {
        return new PlannedItem(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3, 1L, Map.of(), 60, null);
    }
}
