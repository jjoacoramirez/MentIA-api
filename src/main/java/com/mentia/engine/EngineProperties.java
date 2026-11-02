package com.mentia.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;

/**
 * {@code mentia.engine.*}: rango de niveles y nivel de los ítems de la evaluación inicial.
 */
@Validated
@ConfigurationProperties(prefix = "mentia.engine")
public record EngineProperties(
        @Min(1) int initialLevel,
        @Min(1) int levelMin,
        @Min(1) int levelMax) {

    @AssertTrue(message = "Se necesita level-min <= initial-level <= level-max")
    public boolean isConsistent() {
        return levelMin <= initialLevel && initialLevel <= levelMax;
    }

    public int clamp(int level) {
        return Math.max(levelMin, Math.min(levelMax, level));
    }
}
