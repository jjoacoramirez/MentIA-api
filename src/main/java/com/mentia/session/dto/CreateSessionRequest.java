package com.mentia.session.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Si se omite {@code durationMinutes}, se usa la duración elegida en el perfil.
 */
public record CreateSessionRequest(
        @Min(value = 5, message = "La sesión tiene que durar al menos 5 minutos")
        @Max(value = 30, message = "La sesión puede durar hasta 30 minutos")
        Integer durationMinutes) {
}
