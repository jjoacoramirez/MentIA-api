package com.mentia.session;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.session.domain.SessionType;
import com.mentia.session.domain.TrainingSession;

/**
 * Persiste una sesión con sus ítems ya decididos. Qué ítems lleva lo deciden la evaluación inicial (BACK-07)
 * y la generación de sesiones (BACK-09); esta clase solo los guarda en orden.
 */
@Component
public class SessionFactory {

    private final SessionRepository repository;
    private final Clock clock;

    public SessionFactory(SessionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TrainingSession create(UUID userId, SessionType type, int plannedMinutes, List<PlannedItem> items,
            List<Map<String, Object>> rationale) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Una sesión necesita al menos un ítem");
        }
        TrainingSession session = TrainingSession.generate(userId, type, plannedMinutes, rationale, Instant.now(clock));
        items.forEach(i -> session.addItem(i.exerciseId(), i.variantId(), i.categoryId(), i.difficultyLevel(),
                i.seed(), i.params()));
        return repository.saveAndFlush(session);
    }

    public record PlannedItem(
            UUID exerciseId,
            UUID variantId,
            UUID categoryId,
            int difficultyLevel,
            long seed,
            Map<String, Object> params) {
    }
}
