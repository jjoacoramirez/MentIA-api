package com.mentia.gamification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

/**
 * {@code mentia.gamification.*} (docs/plan-backend.md, sección 7). La gamificación premia el uso y la constancia
 * dentro de MentIA.
 */
@Validated
@ConfigurationProperties(prefix = "mentia.gamification")
public record GamificationProperties(
        @Min(0) int xpPerSession,
        @Min(0) int xpPerItem,
        @Min(0) int xpAccuracyBonus,
        @DecimalMin("0.0") @DecimalMax("1.0") double accuracyBonusThreshold,
        @Min(1) int xpPerLevelFactor) {
}
