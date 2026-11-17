package com.mentia.notification;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * {@code mentia.notification.*}. Las claves VAPID y el subject vienen del entorno y solo hacen falta con
 * {@code enabled = true}. La clave privada nunca se loguea ni sale en una respuesta.
 */
@Validated
@ConfigurationProperties(prefix = "mentia.notification")
public record NotificationProperties(
        boolean enabled,
        String vapidPublicKey,
        String vapidPrivateKey,
        String subject,
        @NotBlank String reminderCron,
        @Min(1) int minStreak,
        @Min(1) int maxSubscriptionsPerUser,
        @NotNull Duration ttl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotEmpty List<String> allowedHostSuffixes) {

    @Override
    public String toString() {
        return "NotificationProperties[enabled=" + enabled + "]";
    }
}
