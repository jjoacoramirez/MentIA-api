package com.mentia.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

/**
 * {@code mentia.engine.selection.*}: cómo se reparten los ítems de una sesión entre categorías.
 * Peso de una categoría = {@code 1 + weakCategoryBoost × (100 − score) / 100}.
 */
@Validated
@ConfigurationProperties(prefix = "mentia.engine.selection")
public record SelectionProperties(
        @DecimalMin("0.0") double weakCategoryBoost,
        @Min(1) int maxItems) {
}
