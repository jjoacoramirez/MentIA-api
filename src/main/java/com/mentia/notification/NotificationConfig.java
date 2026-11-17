package com.mentia.notification;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.mentia.progress.ProgressService;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {

    @Bean
    PushEndpointPolicy pushEndpointPolicy(NotificationProperties properties) {
        return new PushEndpointPolicy(properties.allowedHostSuffixes());
    }

    /**
     * Apagado (por defecto) no hace falta ninguna clave. Prendido, una clave mal cargada corta el arranque: es un
     * error de configuración y no un "sin avisos" silencioso.
     */
    @Bean
    PushSender pushSender(NotificationProperties properties, PushEndpointPolicy policy, Clock clock) {
        if (!properties.enabled()) {
            return endpoint -> PushSender.Result.FAILED;
        }
        VapidSigner signer = new VapidSigner(properties.vapidPublicKey(), properties.vapidPrivateKey(),
                properties.subject(), clock);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return new WebPushSender(RestClient.builder().requestFactory(factory).build(), signer, policy,
                properties.ttl().toSeconds());
    }

    @Bean
    @ConditionalOnProperty(prefix = "mentia.notification", name = "enabled", havingValue = "true")
    StreakReminderJob streakReminderJob(NotificationService notifications, ProgressService progress,
            PushSender sender, NotificationProperties properties, Clock clock) {
        return new StreakReminderJob(notifications, progress, sender, properties, clock);
    }
}
