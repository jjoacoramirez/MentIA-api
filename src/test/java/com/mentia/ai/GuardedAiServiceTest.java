package com.mentia.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mentia.ai.AiUnavailableException.Reason;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

class GuardedAiServiceTest {

    private final AiClient client = mock(AiClient.class);
    private final AiBudgetGuard guard = mock(AiBudgetGuard.class);
    private final CircuitBreaker breaker = AiConfig.buildCircuitBreaker(
            new AiProperties.CircuitBreaker(2, Duration.ofSeconds(30), 1));
    private final GuardedAiService service = new GuardedAiService(client, guard, breaker);
    private final AiRequest request = new AiRequest(null, "Dame 3 palabras", 300);
    private final UUID user = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(client.available()).thenReturn(true);
    }

    @Test
    void caminoFelizDevuelveLaRespuestaYRegistraElUsoReal() {
        when(client.generate(request)).thenReturn(new AiResponse("casa", 100, 20));

        AiResponse response = service.generate(AiPurpose.USER_FEEDBACK, request, user);

        assertThat(response.text()).isEqualTo("casa");
        verify(guard).check(request, user);
        verify(guard).record(AiPurpose.USER_FEEDBACK, 100, 20, AiUsageResult.OK, user);
    }

    @Test
    void conLaIaDeshabilitadaCortaAntesDeTocarLaBase() {
        when(client.available()).thenReturn(false);

        assertThatThrownBy(() -> service.generate(AiPurpose.CONTENT_GENERATION, request))
                .isInstanceOfSatisfying(AiUnavailableException.class, e -> assertThat(e.reason()).isEqualTo(Reason.DISABLED));
        verifyNoInteractions(guard);
    }

    @Test
    void siUnLimiteSeSuperaNoLlamaAlProveedorYRegistraElBloqueo() {
        doThrow(new AiUnavailableException(Reason.LIMIT_EXCEEDED, "limit")).when(guard).check(request, user);

        assertThatThrownBy(() -> service.generate(AiPurpose.USER_FEEDBACK, request, user))
                .isInstanceOfSatisfying(AiUnavailableException.class, e -> assertThat(e.reason()).isEqualTo(Reason.LIMIT_EXCEEDED));
        verify(client, never()).generate(any());
        verify(guard).record(AiPurpose.USER_FEEDBACK, 0, 0, AiUsageResult.BLOCKED, user);
    }

    @Test
    void unaFallaDelProveedorSeConvierteEnUnavailableYSeRegistraComoError() {
        when(client.generate(request)).thenThrow(new AiProviderException("HTTP 500", true));

        assertThatThrownBy(() -> service.generate(AiPurpose.CONTENT_GENERATION, request))
                .isInstanceOfSatisfying(AiUnavailableException.class, e -> assertThat(e.reason()).isEqualTo(Reason.PROVIDER_ERROR));
        verify(guard).record(AiPurpose.CONTENT_GENERATION, 0, 0, AiUsageResult.ERROR, null);
    }

    @Test
    void conElCircuitoAbiertoNoSeLlamaAlProveedor() {
        when(client.generate(request)).thenThrow(new AiProviderException("HTTP 500", true));
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> service.generate(AiPurpose.CONTENT_GENERATION, request))
                    .isInstanceOf(AiUnavailableException.class);
        }

        assertThatThrownBy(() -> service.generate(AiPurpose.CONTENT_GENERATION, request))
                .isInstanceOfSatisfying(AiUnavailableException.class, e -> assertThat(e.reason()).isEqualTo(Reason.CIRCUIT_OPEN));
        // 2 intentos reales; el tercero ni salió.
        verify(client, org.mockito.Mockito.times(2)).generate(request);
        verify(guard).record(AiPurpose.CONTENT_GENERATION, 0, 0, AiUsageResult.BLOCKED, null);
    }

    @Test
    void siFallaElRegistroNoSePierdeLaRespuestaYaPagada() {
        when(client.generate(request)).thenReturn(new AiResponse("casa", 100, 20));
        doThrow(new IllegalStateException("db down")).when(guard).record(any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), any(), any());

        assertThat(service.generate(AiPurpose.CONTENT_GENERATION, request).text()).isEqualTo("casa");
    }
}
