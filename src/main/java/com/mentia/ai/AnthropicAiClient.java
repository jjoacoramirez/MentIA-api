package com.mentia.ai;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Cliente de la API de mensajes de Anthropic con {@link RestClient}.
 *
 * <p>Reintenta con backoff exponencial solo ante fallas transitorias (timeout, 408, 429, 5xx). Nunca loguea la key
 * ni los prompts, y los mensajes de error llevan solo el código de estado (no el cuerpo de la respuesta).
 */
public class AnthropicAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicAiClient.class);

    /** Pausa entre reintentos; se inyecta para que los tests no esperen de verdad. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final RestClient restClient;
    private final AiProperties properties;
    private final Sleeper sleeper;

    public AnthropicAiClient(RestClient restClient, AiProperties properties) {
        this(restClient, properties, duration -> Thread.sleep(duration.toMillis()));
    }

    AnthropicAiClient(RestClient restClient, AiProperties properties, Sleeper sleeper) {
        this.restClient = restClient;
        this.properties = properties;
        this.sleeper = sleeper;
    }

    @Override
    public AiResponse generate(AiRequest request) {
        Map<String, Object> payload = buildPayload(request);
        int maxAttempts = properties.maxRetries() + 1;
        for (int attempt = 1; ; attempt++) {
            try {
                return parse(call(payload));
            } catch (AiProviderException e) {
                if (!e.isTransient() || attempt >= maxAttempts) {
                    throw e;
                }
                log.warn("Anthropic call failed (attempt {}/{}), retrying: {}", attempt, maxAttempts, e.getMessage());
                pause(properties.retryBackoff().multipliedBy(1L << (attempt - 1)), e);
            }
        }
    }

    private Map<String, Object> buildPayload(AiRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.anthropic().model());
        payload.put("max_tokens", request.maxTokens());
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            payload.put("system", request.systemPrompt());
        }
        payload.put("messages", List.of(Map.of("role", "user", "content", request.userPrompt())));
        // Los modelos nuevos piensan por defecto y esos tokens se cobran como salida: effort bajo para contenido corto.
        if (properties.anthropic().hasEffort()) {
            payload.put("output_config", Map.of("effort", properties.anthropic().effort()));
        }
        return payload;
    }

    private JsonNode call(Map<String, Object> payload) {
        try {
            JsonNode body = restClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", properties.apiKey())
                    .header("anthropic-version", properties.anthropic().apiVersion())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new AiProviderException("Anthropic returned an empty body", false);
            }
            return body;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            boolean retryable = status == 408 || status == 429 || status >= 500;
            throw new AiProviderException("Anthropic responded with HTTP " + status, retryable);
        } catch (ResourceAccessException e) {
            // Timeout o error de red: el mensaje de la causa puede incluir la URL, nunca headers ni cuerpo.
            throw new AiProviderException("Anthropic is unreachable (" + e.getClass().getSimpleName() + ")", true);
        }
    }

    private AiResponse parse(JsonNode body) {
        if ("refusal".equals(body.path("stop_reason").asText())) {
            throw new AiProviderException("Anthropic declined the request", false);
        }
        // Se leen solo los bloques de texto: los de razonamiento no son contenido.
        StringBuilder text = new StringBuilder();
        for (JsonNode block : body.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }
        if (text.isEmpty()) {
            throw new AiProviderException("Anthropic returned no text (stop_reason=" + body.path("stop_reason").asText() + ")", false);
        }
        JsonNode usage = body.path("usage");
        return new AiResponse(text.toString(), usage.path("input_tokens").asInt(0), usage.path("output_tokens").asInt(0));
    }

    private void pause(Duration duration, AiProviderException cause) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Interrupted while waiting to retry", false, cause);
        }
    }
}
