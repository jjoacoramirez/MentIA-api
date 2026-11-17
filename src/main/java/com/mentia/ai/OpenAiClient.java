package com.mentia.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Cliente de la API de Chat Completions de OpenAI con {@link RestClient}.
 *
 * <p>Mismas reglas que {@link AnthropicAiClient}: reintenta con backoff exponencial solo ante fallas transitorias
 * (timeout, 408, 429, 5xx), nunca loguea la key ni los prompts, y los errores llevan solo el código de estado.
 */
public class OpenAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

    private final RestClient restClient;
    private final AiProperties properties;
    private final AnthropicAiClient.Sleeper sleeper;

    public OpenAiClient(RestClient restClient, AiProperties properties) {
        this(restClient, properties, duration -> Thread.sleep(duration.toMillis()));
    }

    OpenAiClient(RestClient restClient, AiProperties properties, AnthropicAiClient.Sleeper sleeper) {
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
                log.warn("OpenAI call failed (attempt {}/{}), retrying: {}", attempt, maxAttempts, e.getMessage());
                pause(properties.retryBackoff().multipliedBy(1L << (attempt - 1)), e);
            }
        }
    }

    private Map<String, Object> buildPayload(AiRequest request) {
        AiProperties.ProviderSettings settings = properties.openai();
        List<Map<String, String>> messages = new ArrayList<>();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", request.userPrompt()));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", settings.model());
        payload.put("messages", messages);
        // En los modelos con razonamiento este tope incluye los tokens de razonamiento.
        payload.put("max_completion_tokens", request.maxTokens());
        if (settings.hasEffort()) {
            payload.put("reasoning_effort", settings.effort());
        }
        return payload;
    }

    private JsonNode call(Map<String, Object> payload) {
        try {
            JsonNode body = restClient.post()
                    .uri("/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new AiProviderException("OpenAI returned an empty body", false);
            }
            return body;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            boolean retryable = status == 408 || status == 429 || status >= 500;
            throw new AiProviderException("OpenAI responded with HTTP " + status, retryable);
        } catch (ResourceAccessException e) {
            // Timeout o error de red: el mensaje de la causa puede incluir la URL, nunca headers ni cuerpo.
            throw new AiProviderException("OpenAI is unreachable (" + e.getClass().getSimpleName() + ")", true);
        }
    }

    private AiResponse parse(JsonNode body) {
        JsonNode choice = body.path("choices").path(0);
        JsonNode message = choice.path("message");
        if (message.hasNonNull("refusal")) {
            throw new AiProviderException("OpenAI declined the request", false);
        }
        String text = message.path("content").asText("");
        if (text.isBlank()) {
            throw new AiProviderException(
                    "OpenAI returned no text (finish_reason=" + choice.path("finish_reason").asText() + ")", false);
        }
        JsonNode usage = body.path("usage");
        return new AiResponse(text, usage.path("prompt_tokens").asInt(0), usage.path("completion_tokens").asInt(0));
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
