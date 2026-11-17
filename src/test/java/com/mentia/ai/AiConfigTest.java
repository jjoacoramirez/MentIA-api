package com.mentia.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;

/**
 * Carga el {@code application.yml} real (sin base de datos) para comprobar que {@code mentia.ai.*} bindea y valida,
 * y que se elige el cliente correcto según la configuración.
 */
class AiConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(AiConfig.class)
            .withBean(AiUsageRepository.class, () -> mock(AiUsageRepository.class))
            .withBean(Clock.class, Clock::systemUTC)
            // Aislado de lo que haya en el entorno de quien corre el test.
            .withPropertyValues("mentia.ai.api-key=", "mentia.ai.enabled=false", "mentia.ai.provider=openai");

    @Test
    void elYmlRealBindeaConLosValoresIniciales() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            AiProperties p = context.getBean(AiProperties.class);
            assertThat(p.enabled()).isFalse();
            assertThat(p.provider()).isEqualTo("openai");
            assertThat(p.model()).isEqualTo("gpt-4o-mini");
            assertThat(p.pricing().inputUsdPerMillion()).isEqualByComparingTo(new BigDecimal("0.15"));
            assertThat(p.pricing().outputUsdPerMillion()).isEqualByComparingTo(new BigDecimal("0.60"));
            assertThat(p.openai().hasEffort()).as("gpt-4o-mini no razona: no se manda effort").isFalse();
            assertThat(p.anthropic().model()).isEqualTo("claude-haiku-5-5");
            assertThat(p.anthropic().pricing().inputUsdPerMillion()).isEqualByComparingTo(new BigDecimal("0.10"));
            assertThat(p.limits().globalDailyMicroUsd()).isEqualTo(2_000_000L);
            assertThat(p.limits().globalMonthlyMicroUsd()).isEqualTo(20_000_000L);
            assertThat(p.limits().userMonthlyMicroUsd()).isEqualTo(100_000L);
            assertThat(p.limits().userDailyCalls()).isEqualTo(5);
            assertThat(p.maxRetries()).isEqualTo(2);
            assertThat(p.retryBackoff()).isEqualTo(Duration.ofMillis(500));
            assertThat(p.circuitBreaker().failureThreshold()).isEqualTo(5);
            assertThat(p.circuitBreaker().openDuration()).isEqualTo(Duration.ofSeconds(60));
        });
    }

    @Test
    void porDefectoLaIaEstaApagada() {
        runner.run(context -> assertThat(context.getBean(AiClient.class)).isInstanceOf(DisabledAiClient.class));
    }

    @Test
    void habilitadaPeroSinKeyQuedaDeshabilitada() {
        runner.withPropertyValues("mentia.ai.enabled=true")
                .run(context -> assertThat(context.getBean(AiClient.class)).isInstanceOf(DisabledAiClient.class));
    }

    @Test
    void habilitadaYConKeyUsaOpenAiPorDefecto() {
        runner.withPropertyValues("mentia.ai.enabled=true", "mentia.ai.api-key=sk-test")
                .run(context -> assertThat(context.getBean(AiClient.class)).isInstanceOf(OpenAiClient.class));
    }

    @Test
    void conProveedorAnthropicUsaAnthropic() {
        runner.withPropertyValues("mentia.ai.enabled=true", "mentia.ai.api-key=sk-test", "mentia.ai.provider=Anthropic")
                .run(context -> assertThat(context.getBean(AiClient.class)).isInstanceOf(AnthropicAiClient.class));
    }

    @Test
    void unProveedorDesconocidoCortaElArranque() {
        runner.withPropertyValues("mentia.ai.enabled=true", "mentia.ai.api-key=sk-test", "mentia.ai.provider=otro")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void unaConfiguracionInvalidaCortaElArranque() {
        runner.withPropertyValues("mentia.ai.limits.max-tokens-per-call=0")
                .run(context -> assertThat(context).hasFailed());
    }
}
