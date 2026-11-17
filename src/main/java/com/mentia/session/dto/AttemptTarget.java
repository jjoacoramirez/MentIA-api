package com.mentia.session.dto;

import java.util.UUID;

/**
 * Datos del ítem que el módulo de intentos necesita para registrar uno, sin exponer las entidades de sesión.
 */
public record AttemptTarget(
        UUID sessionId,
        UUID sessionItemId,
        UUID exerciseId,
        UUID variantId,
        UUID categoryId,
        int difficultyLevel) {
}
