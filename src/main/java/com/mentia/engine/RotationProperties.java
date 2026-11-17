package com.mentia.engine;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * {@code mentia.engine.rotation.*} (docs/plan-backend.md, sección 7).
 * {@code recencyWeights[r - 1]} es el peso de algo usado hace {@code r} sesiones.
 */
@Validated
@ConfigurationProperties(prefix = "mentia.engine.rotation")
public record RotationProperties(
        @Min(1) int lookbackSessions,
        boolean avoidLastSession,
        @NotNull List<@NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double> recencyWeights) {

    @AssertTrue(message = "recency-weights tiene que tener un peso por cada sesión de lookback-sessions")
    public boolean isOneWeightPerSession() {
        return recencyWeights != null && recencyWeights.size() == lookbackSessions;
    }
}
