package com.mentia.auth.dto;

import com.mentia.security.Role;

/**
 * {@code expiresIn} en segundos hasta el vencimiento. {@code role} le permite al front mostrar herramientas
 * de debug a los admins; los permisos igual los controla el servidor.
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, Role role) {

    public static TokenResponse bearer(String accessToken, long expiresIn, Role role) {
        return new TokenResponse(accessToken, "Bearer", expiresIn, role);
    }
}
