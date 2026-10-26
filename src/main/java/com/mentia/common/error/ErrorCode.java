package com.mentia.common.error;

/**
 * Códigos de error estables del contrato (openapi.yaml). El front depende de estos valores.
 */
public enum ErrorCode {
    VALIDATION_ERROR,
    USERNAME_TAKEN,
    INVALID_CREDENTIALS,
    INVALID_RECOVERY,
    UNAUTHORIZED,
    ASSESSMENT_REQUIRED,
    SESSION_NOT_FOUND,
    SESSION_STATE_INVALID,
    ITEM_NOT_IN_SESSION,
    INTERNAL_ERROR
}
