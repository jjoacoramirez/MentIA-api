package com.mentia.session;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.mentia.session.domain.SessionType;

/**
 * Se publica al completar cualquier sesión, dentro de la misma transacción. Lo consumen gamificación
 * (BACK-18, 19) y métricas (BACK-21); el módulo de sesiones no los conoce.
 *
 * @param categoriesPracticed códigos de las categorías con al menos un intento en la sesión
 */
public record SessionCompletedEvent(
        UUID userId,
        UUID sessionId,
        SessionType type,
        int itemsCompleted,
        double accuracy,
        Set<String> categoriesPracticed,
        Instant occurredAt) {
}
