package com.mentia.feedback;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * {@code mentia.feedback.*}.
 *
 * @param maxTokens      tope de tokens de salida de la IA
 * @param maxChars       largo máximo del comentario; si la IA se pasa, se corta en la última oración o se descarta
 * @param forbiddenTerms términos que no pueden aparecer (nada clínico, regla 2): se comparan sin tildes ni mayúsculas
 */
@Validated
@ConfigurationProperties(prefix = "mentia.feedback")
public record FeedbackProperties(
        @Min(50) int maxTokens,
        @Min(100) int maxChars,
        @NotNull List<String> forbiddenTerms) {
}
