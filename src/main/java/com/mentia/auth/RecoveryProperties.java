package com.mentia.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * {@code mentia.auth.recovery.*}: límite de intentos fallidos de recuperación por username.
 */
@Validated
@ConfigurationProperties(prefix = "mentia.auth.recovery")
public record RecoveryProperties(
        @Min(1) int maxAttempts,
        @NotNull Duration lockout) {
}
