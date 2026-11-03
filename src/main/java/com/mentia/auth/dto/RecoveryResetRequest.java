package com.mentia.auth.dto;

import com.mentia.common.validation.MaxUtf8Bytes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecoveryResetRequest(
        @NotBlank(message = "El nombre de usuario es obligatorio")
        String username,

        @NotBlank(message = "El código de recuperación es obligatorio")
        @Size(max = 40, message = "El código de recuperación no es válido")
        String recoveryCode,

        @NotNull(message = "La contraseña nueva es obligatoria")
        @Size(min = 10, max = 72, message = "La contraseña debe tener entre 10 y 72 caracteres")
        @MaxUtf8Bytes(value = 72, message = "La contraseña es demasiado larga")
        String newPassword) {

    @Override
    public String toString() {
        return "RecoveryResetRequest[username=" + username + ", recoveryCode=***, newPassword=***]";
    }
}
