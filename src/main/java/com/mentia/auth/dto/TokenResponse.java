package com.mentia.auth.dto;

/**
 * {@code expiresIn} en segundos hasta el vencimiento.
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    public static TokenResponse bearer(String accessToken, long expiresIn) {
        return new TokenResponse(accessToken, "Bearer", expiresIn);
    }
}
