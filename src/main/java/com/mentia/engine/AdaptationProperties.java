package com.mentia.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Umbrales del ajuste de nivel por categoría (docs/plan-backend.md, sección 7). Son una propuesta inicial:
 * se cambian en {@code application.yml}, sin tocar código.
 */
@Validated
@ConfigurationProperties(prefix = "mentia.engine")
public record AdaptationProperties(
        @DecimalMin("0.0") @DecimalMax("1.0") double accuracyUp,
        @DecimalMin("0.0") @DecimalMax("1.0") double accuracyDown,
        @DecimalMin(value = "1.0") double responseTimeTolerance,
        @Valid @NotNull LevelProgress levelProgress) {

    /** Puntos de 0 a {@link #MAX} dentro de cada nivel: 0 es el piso y {@link #MAX} el techo. */
    public static final int MAX = 100;

    @AssertTrue(message = "accuracy-down tiene que ser menor que accuracy-up")
    public boolean isOrdered() {
        return accuracyDown < accuracyUp;
    }

    /**
     * @param start        progreso al entrar a un nivel (punto de partida, subida o bajada)
     * @param pointsUp     una actividad con precisión {@code >= accuracy-up} y tiempo dentro de la tolerancia
     * @param pointsUpSlow una actividad con precisión {@code >= accuracy-up} pero más lenta que la tolerancia
     * @param pointsDown   lo que resta una actividad con precisión {@code < accuracy-down}
     */
    public record LevelProgress(
            @Min(1) @Max(MAX - 1) int start,
            @Min(0) @Max(MAX) int pointsUp,
            @Min(0) @Max(MAX) int pointsUpSlow,
            @Min(0) @Max(MAX) int pointsDown) {
    }
}
