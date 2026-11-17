package com.mentia.ai;

/** Puerto hacia el proveedor de IA. El resto del código no sabe qué proveedor hay detrás. */
public interface AiClient {

    /**
     * @throws AiProviderException si el proveedor falla
     * @throws AiUnavailableException si la IA está deshabilitada
     */
    AiResponse generate(AiRequest request);

    /** {@code false} si la IA está deshabilitada: permite cortar antes de tocar la base. */
    default boolean available() {
        return true;
    }
}
