package com.mentia.auth.dto;

import com.mentia.common.validation.MaxUtf8Bytes;

import jakarta.validation.constraints.NotBlank;

public record RegenerateRecoveryRequest(
        @NotBlank(message = "La contraseña es obligatoria")
        @MaxUtf8Bytes(value = 72, message = "La contraseña es demasiado larga")
        String password) {

    @Override
    public String toString() {
        return "RegenerateRecoveryRequest[password=***]";
    }
}
