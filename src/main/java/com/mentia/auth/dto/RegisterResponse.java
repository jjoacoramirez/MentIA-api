package com.mentia.auth.dto;

import java.util.UUID;

/**
 * {@code recoveryCode} es null hasta que se implemente la recuperación de cuenta (BACK-03B).
 */
public record RegisterResponse(UUID userId, String recoveryCode) {
}
