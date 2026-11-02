package com.mentia.security;

import java.time.Instant;
import java.util.UUID;

/**
 * Principal de una request autenticada. Los controllers lo reciben con {@code @AuthenticationPrincipal}.
 * {@code tokenVersion} permite invalidar de una vez todos los tokens de un usuario (ver {@link TokenRevocationService}).
 */
public record AuthenticatedUser(UUID userId, String jti, Instant issuedAt, Instant expiresAt, int tokenVersion,
        Role role) {
}
