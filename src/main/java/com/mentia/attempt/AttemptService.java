package com.mentia.attempt;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.attempt.domain.Attempt;
import com.mentia.attempt.dto.AttemptOutcome;
import com.mentia.attempt.dto.AttemptRequest;
import com.mentia.attempt.dto.AttemptResponse;
import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;
import com.mentia.session.SessionService;
import com.mentia.session.dto.AttemptTarget;

/**
 * Registro de intentos, idempotente por {@code clientAttemptId}: el cliente puede reintentar sin duplicar.
 * <p>
 * No abre una transacción propia a propósito: si dos reintentos llegan a la vez, el segundo choca con la
 * restricción única en la base y, como el insert falló en su propia transacción, todavía se puede leer el
 * intento original y devolverlo.
 */
@Service
public class AttemptService {

    private final AttemptRepository repository;
    private final SessionService sessionService;
    private final Clock clock;

    public AttemptService(AttemptRepository repository, SessionService sessionService, Clock clock) {
        this.repository = repository;
        this.sessionService = sessionService;
        this.clock = clock;
    }

    public Recorded record(UUID userId, UUID sessionId, AttemptRequest request) {
        sessionService.requireOwned(userId, sessionId);

        // Un reintento devuelve el original aunque la sesión ya no esté en curso.
        Optional<Recorded> retry = previous(userId, sessionId, request);
        if (retry.isPresent()) {
            return retry.get();
        }

        AttemptTarget target = sessionService.attemptTarget(userId, sessionId, request.sessionItemId());
        if (repository.existsBySessionItemId(target.sessionItemId())) {
            throw conflict("Ese ejercicio ya tiene un resultado registrado.");
        }

        Attempt attempt = Attempt.record(userId, request.clientAttemptId(), target, request.response(),
                request.isCorrect(), request.accuracy(), request.responseTimeMs(), request.metrics(),
                Instant.now(clock));
        try {
            return new Recorded(AttemptResponse.from(repository.saveAndFlush(attempt)), true);
        } catch (DataIntegrityViolationException e) {
            return previous(userId, sessionId, request)
                    .orElseThrow(() -> conflict("Ese ejercicio ya tiene un resultado registrado."));
        }
    }

    @Transactional(readOnly = true)
    public List<AttemptOutcome> outcomes(UUID sessionId) {
        return repository.findBySessionId(sessionId).stream().map(AttemptOutcome::from).toList();
    }

    private Optional<Recorded> previous(UUID userId, UUID sessionId, AttemptRequest request) {
        return repository.findByUserIdAndClientAttemptId(userId, request.clientAttemptId())
                .map(existing -> {
                    if (!existing.isRetryOf(sessionId, request.sessionItemId())) {
                        throw conflict("Ese identificador de intento ya se usó para otro ejercicio.");
                    }
                    return new Recorded(AttemptResponse.from(existing), false);
                });
    }

    private static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.ATTEMPT_CONFLICT, detail);
    }

    /** {@code created = false} cuando es un reintento de un intento ya registrado. */
    public record Recorded(AttemptResponse attempt, boolean created) {
    }
}
