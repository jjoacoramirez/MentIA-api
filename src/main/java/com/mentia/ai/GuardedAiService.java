package com.mentia.ai;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mentia.ai.AiUnavailableException.Reason;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/**
 * Único punto de entrada para usar IA: límites → circuit breaker → proveedor → registro de uso.
 *
 * <p>Cualquier falla sale como {@link AiUnavailableException}; el consumidor decide el fallback. No se usa en medio
 * de una sesión de entrenamiento: el contenido se genera por lotes antes de que haga falta.
 */
public class GuardedAiService {

    private static final Logger log = LoggerFactory.getLogger(GuardedAiService.class);

    private final AiClient client;
    private final AiBudgetGuard guard;
    private final CircuitBreaker circuitBreaker;

    public GuardedAiService(AiClient client, AiBudgetGuard guard, CircuitBreaker circuitBreaker) {
        this.client = client;
        this.guard = guard;
        this.circuitBreaker = circuitBreaker;
    }

    /** Uso global (por ejemplo generación por lotes): no cuenta contra la cuota de ningún usuario. */
    public AiResponse generate(AiPurpose purpose, AiRequest request) {
        return generate(purpose, request, null);
    }

    /** @param userId quien dispara la llamada; su cuota se aplica y se actualiza. {@code null} = uso global. */
    public AiResponse generate(AiPurpose purpose, AiRequest request, UUID userId) {
        if (!client.available()) {
            throw new AiUnavailableException(Reason.DISABLED, "AI is disabled");
        }
        try {
            guard.check(request, userId);
        } catch (AiUnavailableException e) {
            recordSafely(purpose, 0, 0, AiUsageResult.BLOCKED, userId);
            throw e;
        }

        AiResponse response;
        try {
            response = circuitBreaker.executeSupplier(() -> client.generate(request));
        } catch (CallNotPermittedException e) {
            recordSafely(purpose, 0, 0, AiUsageResult.BLOCKED, userId);
            throw new AiUnavailableException(Reason.CIRCUIT_OPEN, "AI provider is temporarily paused", e);
        } catch (RuntimeException e) {
            recordSafely(purpose, 0, 0, AiUsageResult.ERROR, userId);
            throw new AiUnavailableException(Reason.PROVIDER_ERROR, "AI provider call failed", e);
        }

        recordSafely(purpose, response.tokensIn(), response.tokensOut(), AiUsageResult.OK, userId);
        return response;
    }

    /** Si falla el registro no se pierde una respuesta que ya se pagó; se avisa en el log. */
    private void recordSafely(AiPurpose purpose, int tokensIn, int tokensOut, AiUsageResult result, UUID userId) {
        try {
            guard.record(purpose, tokensIn, tokensOut, result, userId);
        } catch (RuntimeException e) {
            log.error("Could not record AI usage (purpose={}, result={})", purpose, result, e);
        }
    }
}
