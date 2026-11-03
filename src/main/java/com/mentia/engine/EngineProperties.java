package com.mentia.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

/**
 * {@code mentia.engine.*}: rango de niveles, nivel de la evaluación inicial y peso de la sesión nueva en el score.
 */
@Validated
@ConfigurationProperties(prefix = "mentia.engine")
public record EngineProperties(
        @Min(1) int initialLevel,
        @Min(1) int levelMin,
        @Min(1) int levelMax,
        @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0") double emaAlpha) {

    @AssertTrue(message = "Se necesita level-min <= initial-level <= level-max")
    public boolean isConsistent() {
        return levelMin <= initialLevel && initialLevel <= levelMax;
    }

    public int clamp(int level) {
        return Math.max(levelMin, Math.min(levelMax, level));
    }
}
