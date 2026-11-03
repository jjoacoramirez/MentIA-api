package com.mentia.auth.dto;

/**
 * El código se muestra una sola vez: el servidor solo guarda su hash.
 */
public record RecoveryCodeResponse(String recoveryCode) {
}
