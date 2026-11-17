package com.mentia.auth.dto;

import java.util.UUID;

/**
 * {@code recoveryCode} se muestra una sola vez: el servidor solo guarda su hash.
 */
public record RegisterResponse(UUID userId, String recoveryCode) {

    @Override
    public String toString() {
        return "RegisterResponse[userId=" + userId + ", recoveryCode=***]";
    }
}
