package com.mentia.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Schema {@code PushSubscriptionRequest}: la URL de suscripción que entrega el navegador. */
public record PushSubscriptionRequest(@NotBlank @Size(max = 2048) String endpoint) {
}
