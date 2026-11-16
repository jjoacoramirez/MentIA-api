package com.mentia.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

class AiCircuitBreakerTest {

    private final CircuitBreaker breaker = AiConfig.buildCircuitBreaker(
            new AiProperties.CircuitBreaker(3, Duration.ofSeconds(30), 1));

    private void fail(boolean transientFailure) {
        assertThatThrownBy(() -> breaker.executeRunnable(() -> {
            throw new AiProviderException("boom", transientFailure);
        })).isInstanceOf(AiProviderException.class);
    }

    @Test
    void seAbreTrasNFallasTransitoriasSeguidas() {
        fail(true);
        fail(true);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        fail(true);

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> breaker.executeRunnable(() -> { }))
                .isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    void unExitoEnElMedioImpideQueSeAbra() {
        fail(true);
        fail(true);
        breaker.executeRunnable(() -> { });
        fail(true);
        fail(true);

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void lasFallasNoTransitoriasNoLoAbren() {
        for (int i = 0; i < 10; i++) {
            fail(false);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void pasadoElTiempoPruebaConUnaLlamadaYSiAndaSeCierra() {
        fail(true);
        fail(true);
        fail(true);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        breaker.transitionToHalfOpenState(); // equivale a que pasó open-duration
        breaker.executeRunnable(() -> { });

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void siLaLlamadaDePruebaFallaVuelveAAbrirse() {
        fail(true);
        fail(true);
        fail(true);

        breaker.transitionToHalfOpenState();
        fail(true);

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
