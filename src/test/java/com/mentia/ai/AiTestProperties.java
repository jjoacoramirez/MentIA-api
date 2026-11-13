package com.mentia.ai;

import java.math.BigDecimal;
import java.time.Duration;

/** Propiedades de IA para tests unitarios, con los mismos valores que {@code application.yml}. */
final class AiTestProperties {

    private AiTestProperties() {
    }

    static AiProperties.Limits limits(String globalDaily, String globalMonthly, String userMonthly, int userDailyCalls) {
        return new AiProperties.Limits(new BigDecimal(globalDaily), new BigDecimal(globalMonthly),
                new BigDecimal(userMonthly), userDailyCalls, 1024, 8000);
    }

    static AiProperties with(AiProperties.Limits limits, int maxRetries) {
        return new AiProperties(true, "anthropic", "test-key-123", "claude-haiku-5-5", "https://api.anthropic.test",
                "2023-06-01", "low", Duration.ofSeconds(1), Duration.ofSeconds(1), maxRetries, Duration.ofMillis(10),
                new AiProperties.Pricing(new BigDecimal("0.10"), new BigDecimal("0.50")), limits,
                new AiProperties.CircuitBreaker(3, Duration.ofSeconds(30), 1));
    }

    static AiProperties defaults() {
        return with(limits("2.00", "20.00", "0.10", 5), 2);
    }
}
