package com.mentia.session;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * {@code mentia.session.*}: una sesión abierta más vieja que {@code staleAfter} se abandona al pedir una nueva.
 */
@Validated
@ConfigurationProperties(prefix = "mentia.session")
public record SessionProperties(@NotNull Duration staleAfter) {
}
