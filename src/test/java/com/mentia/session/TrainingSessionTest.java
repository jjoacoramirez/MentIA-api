package com.mentia.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mentia.session.domain.SessionItem;
import com.mentia.session.domain.SessionStatus;
import com.mentia.session.domain.SessionType;
import com.mentia.session.domain.TrainingSession;

class TrainingSessionTest {

    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");

    @Test
    void losItemsSeNumeranEnOrdenDesdeUno() {
        TrainingSession session = TrainingSession.generate(UUID.randomUUID(), SessionType.TRAINING, 10, List.of(), NOW);

        SessionItem first = session.addItem(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3, 1L, Map.of());
        SessionItem second = session.addItem(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3, 2L, Map.of());

        assertThat(first.getPosition()).isEqualTo(1);
        assertThat(second.getPosition()).isEqualTo(2);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.GENERATED);
    }

    @Test
    void soloSeIniciaUnaSesionGenerada() {
        TrainingSession session = TrainingSession.generate(UUID.randomUUID(), SessionType.TRAINING, 10, List.of(), NOW);

        session.start(NOW);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(session.getStartedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> session.start(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> session.addItem(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, 1L, Map.of()))
                .as("una sesión iniciada ya no cambia sus ítems")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sabeDeQuienEs() {
        UUID owner = UUID.randomUUID();
        TrainingSession session = TrainingSession.generate(owner, SessionType.INITIAL_ASSESSMENT, 10, List.of(), NOW);

        assertThat(session.isOwnedBy(owner)).isTrue();
        assertThat(session.isOwnedBy(UUID.randomUUID())).isFalse();
    }
}
