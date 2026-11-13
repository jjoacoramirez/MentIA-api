package com.mentia.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code mentia.ai.*}. El proveedor sale de {@code MENTIA_AI_PROVIDER} ({@code openai} o {@code anthropic}) y la key
 * de {@code MENTIA_AI_API_KEY}: puede estar vacía (la IA queda deshabilitada). Modelo, URL y precios están por
 * proveedor. Los montos en USD se convierten a micro-USD enteros antes de comparar.
 */
@Validated
@ConfigurationProperties(prefix = "mentia.ai")
public record AiProperties(
        boolean enabled,
        @NotBlank String provider,
        String apiKey,
        @Valid @NotNull ProviderSettings openai,
        @Valid @NotNull ProviderSettings anthropic,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Min(0) int maxRetries,
        @NotNull Duration retryBackoff,
        @Valid @NotNull Limits limits,
        @Valid @NotNull CircuitBreaker circuitBreaker) {

    static final String OPENAI = "openai";
    static final String ANTHROPIC = "anthropic";

    /**
     * @param apiVersion solo Anthropic ({@code anthropic-version}); en OpenAI se ignora
     * @param effort cuánto razona el modelo; vacío = no se manda (los modelos de OpenAI sin razonamiento lo rechazan)
     */
    public record ProviderSettings(
            @NotBlank String model,
            @NotBlank String baseUrl,
            String apiVersion,
            String effort,
            @Valid @NotNull Pricing pricing) {

        boolean hasEffort() {
            return effort != null && !effort.isBlank();
        }
    }

    /** La configuración del proveedor elegido. Un proveedor desconocido es un error de configuración. */
    public ProviderSettings active() {
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case OPENAI -> openai;
            case ANTHROPIC -> anthropic;
            default -> throw new IllegalStateException("Unknown mentia.ai.provider: " + provider);
        };
    }

    /** Modelo del proveedor elegido (se guarda en {@code ai_usage.model}). */
    public String model() {
        return active().model();
    }

    public Pricing pricing() {
        return active().pricing();
    }

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
        return "AiProperties[enabled=" + enabled + ", provider=" + provider + ", apiKey=" + (apiKey == null || apiKey.isBlank() ? "<empty>" : "<redacted>") + "]";
    }
}
