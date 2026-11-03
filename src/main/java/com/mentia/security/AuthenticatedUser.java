package com.mentia.security;

import java.time.Instant;
import java.util.UUID;

/**
 * Principal de una request autenticada. Los controllers lo reciben con {@code @AuthenticationPrincipal}.
 */
public record AuthenticatedUser(UUID userId, String jti, Instant issuedAt, Instant expiresAt) {
}
