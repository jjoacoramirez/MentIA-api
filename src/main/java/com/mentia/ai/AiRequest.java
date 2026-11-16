package com.mentia.ai;

/**
 * Pedido de generación de texto. Debe llevar solo parámetros del ejercicio (categoría, nivel, cantidad),
 * nunca IDs, emails ni datos de usuario.
 */
public record AiRequest(String systemPrompt, String userPrompt, int maxTokens) {

    public AiRequest {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("userPrompt is required");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
    }

    /** Largo total de los prompts en caracteres (para el límite de tamaño). */
    public int promptLength() {
        return userPrompt.length() + (systemPrompt == null ? 0 : systemPrompt.length());
    }

    /** Sin el contenido: los prompts no se loguean completos. */
    @Override
    public String toString() {
        return "AiRequest[promptChars=" + promptLength() + ", maxTokens=" + maxTokens + "]";
    }
}
