package com.mentia.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code mentia.security.*}. El secreto viene de la variable de entorno {@code MENTIA_JWT_SECRET}.
 */
@Validated
@ConfigurationProperties(prefix = "mentia.security")
public record SecurityProperties(
        @NotBlank String jwtSecret,
        @NotNull Duration jwtTtl) {
}
