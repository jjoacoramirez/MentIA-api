package com.mentia.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * {@code mentia.catalog.generation.*}: generación de contenido por lotes con IA.
 *
 * @param maxVariantsPerRequest tope de variantes nuevas por pedido del admin (cada una es una llamada a la IA)
 * @param maxTokens             tope de tokens de salida por llamada
 * @param activateGenerated     si lo generado y validado queda activo; en {@code false} queda para revisar
 */
@Validated
@ConfigurationProperties(prefix = "mentia.catalog.generation")
public record ContentGenerationProperties(
        @Min(1) @Max(10) int maxVariantsPerRequest,
        @Min(100) int maxTokens,
        boolean activateGenerated) {
}
