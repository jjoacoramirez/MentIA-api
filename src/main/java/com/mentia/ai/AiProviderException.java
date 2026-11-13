package com.mentia.ai;

/**
 * Falla al llamar al proveedor. Nunca incluye el cuerpo de la respuesta ni los prompts en el mensaje.
 *
 * <p>{@code transientFailure} distingue lo que cuenta para el circuit breaker (timeouts, 429, 5xx) de lo que no
 * (key inválida, pedido rechazado, respuesta vacía): reintentar o esperar no lo arregla.
 */
public class AiProviderException extends RuntimeException {

    private final boolean transientFailure;

    public AiProviderException(String message, boolean transientFailure) {
        super(message);
        this.transientFailure = transientFailure;
    }

    public AiProviderException(String message, boolean transientFailure, Throwable cause) {
        super(message, cause);
        this.transientFailure = transientFailure;
    }

    public boolean isTransient() {
        return transientFailure;
    }
}
