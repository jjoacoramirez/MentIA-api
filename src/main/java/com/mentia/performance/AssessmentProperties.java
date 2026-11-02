package com.mentia.performance;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

/**
 * {@code mentia.engine.assessment.*} (docs/plan-backend.md, sección 7).
 */
@Validated
@ConfigurationProperties(prefix = "mentia.engine.assessment")
public record AssessmentProperties(
        @Min(1) int exercisesPerCategory,
        @NotEmpty List<@Valid Threshold> initialLevels) {

    /** Si la precisión promedio de la categoría es {@code >= minAccuracy}, el nivel inicial es {@code level}. */
    public record Threshold(
            @DecimalMin("0.0") @DecimalMax("1.0") double minAccuracy,
            @Min(1) int level) {
    }

    @AssertTrue(message = "initial-levels necesita un umbral con min-accuracy 0 para que toda precisión tenga nivel")
    public boolean isCoveringZero() {
        return initialLevels != null && initialLevels.stream().anyMatch(t -> t.minAccuracy() == 0.0);
    }
}
