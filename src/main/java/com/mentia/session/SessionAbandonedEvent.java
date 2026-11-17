package com.mentia.session;

import java.time.Instant;
import java.util.UUID;

import com.mentia.session.domain.SessionType;

/**
 * Se publica cuando el usuario abandona una sesión. Lo consume métricas (BACK-21).
 */
public record SessionAbandonedEvent(UUID userId, UUID sessionId, SessionType type, Instant occurredAt) {
}
