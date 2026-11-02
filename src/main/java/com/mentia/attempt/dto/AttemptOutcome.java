package com.mentia.attempt.dto;

import java.util.UUID;

import com.mentia.attempt.domain.Attempt;

/**
 * Resultado de un intento, para consolidar una sesión sin exponer la entidad.
 */
public record AttemptOutcome(UUID sessionItemId, UUID categoryId, boolean correct, double accuracy,
        int responseTimeMs) {

    public static AttemptOutcome from(Attempt attempt) {
        return new AttemptOutcome(attempt.getSessionItemId(), attempt.getCategoryId(), attempt.isCorrect(),
                attempt.getAccuracy(), attempt.getResponseTimeMs());
    }
}
