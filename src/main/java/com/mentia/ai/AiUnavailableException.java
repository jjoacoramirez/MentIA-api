package com.mentia.ai;

/**
 * La IA no se puede usar ahora (apagada, límite superado, circuit breaker abierto o falla del proveedor).
 * Los consumidores deben tener fallback (biblioteca local de contenido, o simplemente sin feedback).
 */
public class AiUnavailableException extends RuntimeException {

    public enum Reason { DISABLED, LIMIT_EXCEEDED, CIRCUIT_OPEN, PROVIDER_ERROR }

    private final Reason reason;

    public AiUnavailableException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AiUnavailableException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
