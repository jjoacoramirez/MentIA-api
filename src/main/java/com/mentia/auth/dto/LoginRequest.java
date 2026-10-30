package com.mentia.auth.dto;

import com.mentia.common.validation.MaxUtf8Bytes;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "El nombre de usuario es obligatorio")
        String username,

        // Nunca puede ser válida una contraseña de más de 72 bytes (límite de BCrypt), así que se corta antes.
        @NotBlank(message = "La contraseña es obligatoria")
        @MaxUtf8Bytes(value = 72, message = "La contraseña es demasiado larga")
        String password) {

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
