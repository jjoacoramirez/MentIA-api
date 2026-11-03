package com.mentia.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

/**
 * Umbrales del ajuste de nivel por categoría (docs/plan-backend.md, sección 7). Son una propuesta inicial:
 * se cambian en {@code application.yml}, sin tocar código.
 */
@Validated
@ConfigurationProperties(prefix = "mentia.engine")
public record AdaptationProperties(
        @DecimalMin("0.0") @DecimalMax("1.0") double accuracyUp,
        @DecimalMin("0.0") @DecimalMax("1.0") double accuracyDown,
        @DecimalMin(value = "1.0") double responseTimeTolerance,
        @Min(1) int minItemsForAdaptation) {

    @AssertTrue(message = "accuracy-down tiene que ser menor que accuracy-up")
    public boolean isOrdered() {
        return accuracyDown < accuracyUp;
    }
}
