package com.mentia.notification;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Manda un push sin contenido (no hace falta cifrar) autenticado con VAPID. No sigue redirecciones en el POST y
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
        if (!policy.isAllowed(endpoint)) {
            return Result.GONE;
        }
        try {
            client.post()
                    .uri(URI.create(endpoint))
                    .header(HttpHeaders.AUTHORIZATION, signer.authorization(endpoint))
                    .header("TTL", Long.toString(ttlSeconds))
                    .header("Urgency", "normal")
                    .header("Topic", "streak")
                    .retrieve()
                    .toBodilessEntity();
            return Result.SENT;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404 || status == 410) {
                return Result.GONE;
            }
            log.warn("Push rechazado por el servicio, status={}", status);
            return Result.FAILED;
        } catch (RuntimeException e) {
            log.warn("No se pudo contactar al servicio de push: {}", e.getClass().getSimpleName());
            return Result.FAILED;
        }
    }
}
