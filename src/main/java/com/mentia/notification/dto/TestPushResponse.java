package com.mentia.notification.dto;

/**
 * Schema {@code TestPushResult}: cuántos dispositivos tiene la cuenta con el recordatorio activado y qué pasó con
 * cada aviso. Solo conteos: nunca las URLs de suscripción.
 */
public record TestPushResponse(int devices, int sent, int removed, int failed) {
}
