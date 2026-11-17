/**
 * Capa de IA (IA-01): puerto {@code AiClient}, límites de costo y circuit breaker.
 *
 * <p>Queda fuera del motor adaptativo: la IA nunca decide dificultad ni selecciona ejercicios. Los consumidores
 * (generación de contenido por lotes, feedback en lenguaje natural) entran por {@link com.mentia.ai.GuardedAiService}
 * y deben tener un fallback para cuando lance {@link com.mentia.ai.AiUnavailableException}.
 */
package com.mentia.ai;
