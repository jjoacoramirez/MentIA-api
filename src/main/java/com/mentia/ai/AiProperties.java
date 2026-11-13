package com.mentia.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code mentia.ai.*}. La key viene de la variable de entorno {@code ANTHROPIC_API_KEY}: puede estar vacía
 * (la IA queda deshabilitada). Los montos en USD se convierten a micro-USD enteros antes de comparar.
 */
@Validated
@ConfigurationProperties(prefix = "mentia.ai")
public record AiProperties(
        boolean enabled,
        @NotBlank String provider,
        String apiKey,
        @NotBlank String model,
        @NotBlank String baseUrl,
        @NotBlank String apiVersion,
        @NotBlank String effort,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Min(0) int maxRetries,
        @NotNull Duration retryBackoff,
        @Valid @NotNull Pricing pricing,
        @Valid @NotNull Limits limits,
        @Valid @NotNull CircuitBreaker circuitBreaker) {

    /** Precio en USD por millón de tokens. */
    public record Pricing(
            @NotNull @DecimalMin("0") BigDecimal inputUsdPerMillion,
            @NotNull @DecimalMin("0") BigDecimal outputUsdPerMillion) {
    }

    public record Limits(
            @NotNull @DecimalMin("0") BigDecimal globalDailyUsd,
            @NotNull @DecimalMin("0") BigDecimal globalMonthlyUsd,
            @NotNull @DecimalMin("0") BigDecimal userMonthlyUsd,
            @Min(0) int userDailyCalls,
            @Min(1) int maxTokensPerCall,
            @Min(1) int maxPromptChars) {

        public long globalDailyMicroUsd() {
            return toMicroUsd(globalDailyUsd);
        }

        public long globalMonthlyMicroUsd() {
            return toMicroUsd(globalMonthlyUsd);
        }

        public long userMonthlyMicroUsd() {
            return toMicroUsd(userMonthlyUsd);
        }
    }

    /** Se abre tras {@code failureThreshold} fallas transitorias seguidas. */
    public record CircuitBreaker(
            @Min(1) int failureThreshold,
            @NotNull Duration openDuration,
            @Min(1) int halfOpenCalls) {
    }

    static long toMicroUsd(BigDecimal usd) {
        return usd.movePointRight(6).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Sin la key, para que no aparezca en ningún log ni mensaje de error. */
    @Override
    public String toString() {
        return "AiProperties[enabled=" + enabled + ", provider=" + provider + ", model=" + model
                + ", apiKey=" + (apiKey == null || apiKey.isBlank() ? "<empty>" : "<redacted>") + "]";
    }
}
