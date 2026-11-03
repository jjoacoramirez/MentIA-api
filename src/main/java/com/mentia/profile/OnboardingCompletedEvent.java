package com.mentia.profile;

import java.time.Instant;
import java.util.UUID;

/**
 * Se publica la primera vez que un usuario completa el onboarding. Lo consume métricas (BACK-21).
 */
public record OnboardingCompletedEvent(UUID userId, Instant occurredAt) {
}
