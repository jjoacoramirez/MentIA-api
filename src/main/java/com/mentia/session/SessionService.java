package com.mentia.session;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;
import com.mentia.session.domain.PlannedItem;
import com.mentia.session.domain.SessionItem;
import com.mentia.session.domain.SessionStatus;
import com.mentia.session.domain.SessionType;
import com.mentia.session.domain.TrainingSession;
import com.mentia.session.dto.AttemptTarget;
import com.mentia.session.dto.SessionResponse;

/**
 * Ciclo de vida de las sesiones: crear, retomar, iniciar y abandonar. El cierre está en
 * {@link SessionLifecycleService} porque necesita los intentos y el perfil de desempeño.
 */
@Service
public class SessionService {

    private final SessionRepository repository;
    private final SessionFactory factory;
    private final SessionViewAssembler assembler;
    private final SessionProperties properties;
    private final Clock clock;

    public SessionService(SessionRepository repository, SessionFactory factory, SessionViewAssembler assembler,
            SessionProperties properties, Clock clock) {
        this.repository = repository;
        this.factory = factory;
        this.assembler = assembler;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public SessionResponse create(UUID userId, SessionType type, int plannedMinutes, List<PlannedItem> items,
            List<Map<String, Object>> rationale) {
        return assembler.toResponse(factory.create(userId, type, plannedMinutes, items, rationale));
    }

    /**
     * La sesión abierta más reciente de ese tipo, para retomarla. Si está vencida ({@code mentia.session.stale-after})
     * se abandona y no se devuelve.
     */
    @Transactional
    public Optional<SessionResponse> resumable(UUID userId, SessionType type) {
        Instant now = Instant.now(clock);
        return repository.findLatestOpen(userId, type)
                .filter(session -> {
                    if (session.isStale(now, properties.staleAfter())) {
                        session.abandon(now);
                        return false;
                    }
                    return true;
                })
                .map(assembler::toResponse);
    }

    /** Abandona todas las sesiones abiertas de ese tipo del usuario. */
    @Transactional
    public void abandonOpen(UUID userId, SessionType type) {
        Instant now = Instant.now(clock);
        repository.findByUserIdAndTypeAndStatusInOrderByCreatedAtDesc(userId, type,
                        List.of(SessionStatus.GENERATED, SessionStatus.IN_PROGRESS))
                .forEach(session -> session.abandon(now));
    }

    /** {@code GENERATED → IN_PROGRESS}. */
    @Transactional
    public SessionResponse start(UUID userId, UUID sessionId) {
        TrainingSession session = load(userId, sessionId);
        if (session.getStatus() != SessionStatus.GENERATED) {
            throw stateInvalid("Esta sesión ya fue iniciada o está cerrada.");
        }
        session.start(Instant.now(clock));
        return assembler.toResponse(session);
    }

    /** 404 si la sesión no existe o es de otro usuario: no se distingue para no filtrar ids ajenos. */
    @Transactional(readOnly = true)
    public void requireOwned(UUID userId, UUID sessionId) {
        load(userId, sessionId);
    }

    /**
     * Valida que se pueda registrar un intento: sesión propia, en curso, y el ítem pertenece a ella.
     */
    @Transactional(readOnly = true)
    public AttemptTarget attemptTarget(UUID userId, UUID sessionId, UUID sessionItemId) {
        TrainingSession session = load(userId, sessionId);
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw stateInvalid("Solo se pueden registrar resultados de una sesión en curso.");
        }
        SessionItem item = session.item(sessionItemId).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                ErrorCode.ITEM_NOT_IN_SESSION, "El ejercicio no pertenece a esta sesión."));
        return new AttemptTarget(session.getId(), item.getId(), item.getExerciseId(), item.getVariantId(),
                item.getCategoryId(), item.getDifficultyLevel());
    }

    TrainingSession load(UUID userId, UUID sessionId) {
        return repository.findById(sessionId)
                .filter(s -> s.isOwnedBy(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SESSION_NOT_FOUND,
                        "No encontramos esa sesión."));
    }

    static ApiException stateInvalid(String detail) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.SESSION_STATE_INVALID, detail);
    }
}
