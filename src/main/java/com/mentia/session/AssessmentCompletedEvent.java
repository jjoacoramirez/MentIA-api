package com.mentia.session;

import java.time.Instant;
import java.util.UUID;

/**
 * Se publica al completar una evaluación inicial. Lo consume métricas (BACK-21).
 */
public record AssessmentCompletedEvent(UUID userId, UUID sessionId, Instant occurredAt) {
}
