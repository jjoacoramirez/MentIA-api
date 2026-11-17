package com.mentia.progress;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * {@code mentia.progress.*}. Racha y objetivo diario son opcionales (ajuste 4 del plan): con
 * {@code streaksEnabled = false} o {@code dailyGoalSessions = 0} el resumen los devuelve en null.
 */
@Validated
@ConfigurationProperties(prefix = "mentia.progress")
public record ProgressProperties(
        @NotNull ZoneId timeZone,
        boolean streaksEnabled,
        @Min(0) int dailyGoalSessions) {
}
