package com.mentia.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Schema {@code PushSubscriptionRequest}: la URL de suscripción que entrega el navegador. */
public record PushSubscriptionRequest(@NotBlank @Size(max = 2048) String endpoint,
        @Size(max = 128) String p256dh, @Size(max = 64) String auth) {
    public PushSubscriptionRequest(String endpoint) { this(endpoint, null, null); }
    @Override public String toString() { return "PushSubscriptionRequest[protegida]"; }
}
