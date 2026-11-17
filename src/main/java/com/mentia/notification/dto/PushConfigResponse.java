package com.mentia.notification.dto;

/** Schema {@code PushConfig}: {@code publicKey} es la clave pública VAPID (no es un secreto) y es null si está apagado. */
public record PushConfigResponse(boolean enabled, String publicKey) {
}
