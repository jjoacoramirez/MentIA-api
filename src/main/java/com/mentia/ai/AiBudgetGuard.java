package com.mentia.ai;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import com.mentia.ai.AiUnavailableException.Reason;

/**
 * Aplica los límites de {@code mentia.ai.limits.*} antes de cada llamada y registra el uso real después.
 *
 * <p>Los límites son "blandos": se compara el gasto ya registrado, sin reservar el costo de la llamada en curso.
 * Con llamadas de centavos el desvío es despreciable, y la barrera dura es el tope de gasto del proveedor.
 * Días y meses son UTC.
 */
public class AiBudgetGuard {

    private final AiProperties properties;
    private final AiUsageRepository repository;
    private final AiCostCalculator costCalculator;
    private final Clock clock;

    public AiBudgetGuard(AiProperties properties, AiUsageRepository repository, AiCostCalculator costCalculator,
            Clock clock) {
        this.properties = properties;
        this.repository = repository;
        this.costCalculator = costCalculator;
        this.clock = clock;
    }

    /**
     * @param userId quien dispara la llamada, o {@code null} si es un uso global (generación por lotes)
     * @throws AiUnavailableException con {@link Reason#LIMIT_EXCEEDED} si algún límite se superó
     */
    public void check(AiRequest request, UUID userId) {
        AiProperties.Limits limits = properties.limits();
        if (request.maxTokens() > limits.maxTokensPerCall()) {
            throw exceeded("maxTokens por llamada");
        }
        if (request.promptLength() > limits.maxPromptChars()) {
            throw exceeded("tamaño del prompt");
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate monthStart = today.withDayOfMonth(1);
        if (repository.globalCostBetween(today, today) >= limits.globalDailyMicroUsd()) {
            throw exceeded("presupuesto global diario");
        }
        if (repository.globalCostBetween(monthStart, today) >= limits.globalMonthlyMicroUsd()) {
            throw exceeded("presupuesto global mensual");
        }
        if (userId != null) {
            if (repository.userCallsOn(userId, today) >= limits.userDailyCalls()) {
                throw exceeded("llamadas diarias por usuario");
            }
            if (repository.userCostBetween(userId, monthStart, today) >= limits.userMonthlyMicroUsd()) {
                throw exceeded("tope mensual por usuario");
            }
        }
    }

    /** Registra un intento. El costo se calcula con los tokens reales; solo las llamadas {@code OK} cuestan. */
    public void record(AiPurpose purpose, int tokensIn, int tokensOut, AiUsageResult result, UUID userId) {
        long cost = result == AiUsageResult.OK ? costCalculator.costMicroUsd(tokensIn, tokensOut) : 0L;
        repository.record(LocalDate.now(clock), clock.instant(), purpose, properties.model(),
                tokensIn, tokensOut, cost, result, userId);
    }

    private static AiUnavailableException exceeded(String limit) {
        return new AiUnavailableException(Reason.LIMIT_EXCEEDED, "AI limit exceeded: " + limit);
    }
}
