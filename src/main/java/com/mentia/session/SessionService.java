package com.mentia.session;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;
import com.mentia.session.domain.SessionItem;
import com.mentia.session.domain.SessionStatus;
import com.mentia.session.domain.TrainingSession;
import com.mentia.session.dto.AttemptTarget;

/**
 * Ciclo de vida de las sesiones. En BACK-12 solo expone lo que necesita el registro de intentos;
 * generación, inicio y cierre llegan con BACK-07, BACK-09 y BACK-13.
 */
@Service
public class SessionService {

    private final SessionRepository repository;

    public SessionService(SessionRepository repository) {
        this.repository = repository;
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
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SESSION_STATE_INVALID,
                    "Solo se pueden registrar resultados de una sesión en curso.");
        }
        SessionItem item = session.item(sessionItemId).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                ErrorCode.ITEM_NOT_IN_SESSION, "El ejercicio no pertenece a esta sesión."));
        return new AttemptTarget(session.getId(), item.getId(), item.getExerciseId(), item.getVariantId(),
                item.getCategoryId(), item.getDifficultyLevel());
    }

    private TrainingSession load(UUID userId, UUID sessionId) {
        return repository.findById(sessionId)
                .filter(s -> s.isOwnedBy(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SESSION_NOT_FOUND,
                        "No encontramos esa sesión."));
    }
}
