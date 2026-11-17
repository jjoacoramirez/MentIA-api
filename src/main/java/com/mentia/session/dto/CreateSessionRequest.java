package com.mentia.session.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Si se omite {@code durationMinutes}, se usa la duración elegida en el perfil. Si se indica {@code category}
 * (código de una categoría activa del MVP), la sesión solo trae ejercicios de esa categoría.
 */
public record CreateSessionRequest(
        @Min(value = 5, message = "La sesión tiene que durar al menos 5 minutos")
        @Max(value = 30, message = "La sesión puede durar hasta 30 minutos")
        Integer durationMinutes,
        String category) {
}
