package com.mentia.attempt.dto;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Resultado de un ítem reportado por el cliente. {@code response} y {@code metrics} dependen de la mecánica.
 */
public record AttemptRequest(
        @NotNull(message = "El identificador del intento es obligatorio")
        UUID clientAttemptId,

        @NotNull(message = "El ítem de la sesión es obligatorio")
        UUID sessionItemId,

        Map<String, Object> response,

        @NotNull(message = "Indicá si la respuesta fue correcta")
        Boolean isCorrect,

        @NotNull(message = "La precisión es obligatoria")
        @DecimalMin(value = "0.0", message = "La precisión va de 0 a 1")
        @DecimalMax(value = "1.0", message = "La precisión va de 0 a 1")
        Double accuracy,

        @NotNull(message = "El tiempo de respuesta es obligatorio")
        @Min(value = 0, message = "El tiempo de respuesta no puede ser negativo")
        Integer responseTimeMs,

        Map<String, Object> metrics) {
}
