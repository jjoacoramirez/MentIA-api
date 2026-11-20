package com.mentia.notification.dto;

import java.util.Map;

/**
 * Schema {@code TestPushResult}: cuántos dispositivos tiene la cuenta con el recordatorio activado y qué pasó con
 * cada aviso. Conteos y códigos de fallo: nunca las URLs de suscripción ni respuestas del proveedor.
 */
public record TestPushResponse(int devices, int sent, int removed, int failed, Map<String, Integer> failures) {
    public TestPushResponse(int devices, int sent, int removed, int failed) {
        this(devices, sent, removed, failed, Map.of());
    }
}
