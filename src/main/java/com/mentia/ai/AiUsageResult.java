package com.mentia.ai;

/** Resultado registrado en {@code ai_usage.result}. */
public enum AiUsageResult {
    OK,
    /** La llamada llegó al proveedor y falló. */
    ERROR,
    /** No se hizo la llamada: un límite o el circuit breaker la frenó. */
    BLOCKED
}
