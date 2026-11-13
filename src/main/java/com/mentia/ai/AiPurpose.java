package com.mentia.ai;

/** Para qué se usa una llamada. Se guarda en {@code ai_usage.purpose}; sumar un valor no requiere migración. */
public enum AiPurpose {
    /** Generación de contenido por lotes (por ejemplo listas de palabras). Costo global, sin usuario. */
    CONTENT_GENERATION,
    /** Feedback en lenguaje natural disparado por una persona. Cuenta contra su cuota. */
    USER_FEEDBACK
}
