package com.mentia.notification;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Manda un push con contenido cifrado cuando la suscripción tiene claves, autenticado con VAPID. No sigue redirecciones en el POST y
 * nunca loguea el endpoint ni el JWT: solo el código de estado.
 */
final class WebPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);

    private final RestClient client;
    private final VapidSigner signer;
    private final PushEndpointPolicy policy;
    private final long ttlSeconds;

    WebPushSender(RestClient client, VapidSigner signer, PushEndpointPolicy policy, long ttlSeconds) {
        this.client = client;
        this.signer = signer;
        this.policy = policy;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public Result send(String endpoint) {
        return send(endpoint, null, null, false);
    }

    @Override
    public Result send(String endpoint, String p256dh, String auth, boolean hasStreak) {
        if (!policy.isAllowed(endpoint)) {
            return Result.GONE;
        }
        try {
            var request = client.post()
                    .uri(URI.create(endpoint))
                    .header(HttpHeaders.AUTHORIZATION, signer.authorization(endpoint))
                    .header("TTL", Long.toString(ttlSeconds))
                    .header("Urgency", "normal")
                    .header("Topic", "streak");
            if (p256dh != null && auth != null) {
                byte[] payload = (hasStreak ? "{\"kind\":\"streak\"}" : "{\"kind\":\"start\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                request.header("Content-Encoding", "aes128gcm")
                        .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                        .body(WebPushEncryption.encrypt(p256dh, auth, payload));
            }
            request.retrieve()
                    .toBodilessEntity();
            return Result.SENT;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404 || status == 410) {
                return Result.GONE;
            }
            log.warn("Push rechazado por el servicio, status={}", status);
            return switch (status) {
                case 401 -> Result.UNAUTHORIZED;
                case 403 -> Result.FORBIDDEN;
                case 429 -> Result.RATE_LIMITED;
                default -> status >= 500 ? Result.PROVIDER_UNAVAILABLE : Result.REJECTED;
            };
        } catch (ResourceAccessException e) {
            log.warn("No se pudo contactar al servicio de push: {}", e.getClass().getSimpleName());
            return Result.CONNECTION_FAILED;
        } catch (java.security.GeneralSecurityException | RuntimeException e) {
            log.warn("No se pudo contactar al servicio de push: {}", e.getClass().getSimpleName());
            return Result.FAILED;
        }
    }
}
