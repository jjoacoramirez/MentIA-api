package com.mentia.profile;

import java.util.UUID;

/**
 * Indica si el usuario ya completó la evaluación inicial. La implementa el módulo de sesiones (BACK-07);
 * mientras no exista una implementación, el perfil informa {@code assessmentCompleted = false}.
 */
public interface AssessmentCompletionQuery {

    boolean isCompleted(UUID userId);
}
