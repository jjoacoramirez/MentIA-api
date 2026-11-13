package com.mentia.ai;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);
    /**
     * Elige el cliente según {@code mentia.ai.*}: sin habilitar o sin key queda {@link DisabledAiClient}.
     * Un proveedor desconocido corta el arranque: es un error de configuración, no un "apagado".
     */
    @Bean
    AiClient aiClient(AiProperties properties) {
        AiProperties.ProviderSettings settings = properties.active();
        if (!properties.enabled()) {
            log.info("AI is disabled (mentia.ai.enabled=false)");
            return new DisabledAiClient("AI is disabled");
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.warn("mentia.ai.enabled=true but MENTIA_AI_API_KEY is empty: AI stays disabled");
            return new DisabledAiClient("AI API key is not configured");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(settings.baseUrl())
                .requestFactory(requestFactory)
                .build();
        log.info("AI enabled with provider={} model={}", properties.provider(), settings.model());
        return AiProperties.ANTHROPIC.equalsIgnoreCase(properties.provider())
                ? new AnthropicAiClient(restClient, properties)
                : new OpenAiClient(restClient, properties);
    }

    @Bean
    AiCostCalculator aiCostCalculator(AiProperties properties) {
        return new AiCostCalculator(properties.pricing());
    }

    @Bean
    AiBudgetGuard aiBudgetGuard(AiProperties properties, AiUsageRepository repository,
            AiCostCalculator costCalculator, Clock clock) {
        return new AiBudgetGuard(properties, repository, costCalculator, clock);
    }

    @Bean
    CircuitBreaker aiCircuitBreaker(AiProperties properties) {
        return buildCircuitBreaker(properties.circuitBreaker());
    }

    @Bean
    GuardedAiService guardedAiService(AiClient client, AiBudgetGuard guard, CircuitBreaker aiCircuitBreaker) {
        return new GuardedAiService(client, guard, aiCircuitBreaker);
    }

    /**
     * Se abre cuando las últimas N llamadas (N = failure-threshold) fallaron todas, es decir N fallas transitorias
     * seguidas. Las fallas no transitorias (key inválida, pedido rechazado) no cuentan ni suman.
     */
    static CircuitBreaker buildCircuitBreaker(AiProperties.CircuitBreaker settings) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.failureThreshold())
                .minimumNumberOfCalls(settings.failureThreshold())
                .failureRateThreshold(100f)
                .waitDurationInOpenState(settings.openDuration())
                .permittedNumberOfCallsInHalfOpenState(settings.halfOpenCalls())
                .recordException(e -> e instanceof AiProviderException p && p.isTransient())
                .ignoreException(e -> e instanceof AiProviderException p && !p.isTransient())
                .build();
        return CircuitBreaker.of("ai-provider", config);
    }
}
