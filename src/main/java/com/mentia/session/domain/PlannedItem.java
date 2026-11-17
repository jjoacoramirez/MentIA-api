package com.mentia.session.domain;

import java.util.Map;
import java.util.UUID;

/**
 * Un ítem ya decidido (ejercicio, variante, nivel y su configuración) listo para agregarse a una sesión.
 * {@code params}, {@code estimatedSeconds} y {@code targetResponseMs} son copia de los del nivel.
 */
public record PlannedItem(
        UUID exerciseId,
        UUID variantId,
        UUID categoryId,
        int difficultyLevel,
        long seed,
        Map<String, Object> params,
        int estimatedSeconds,
        Integer targetResponseMs) {
}
