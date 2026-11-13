package com.mentia.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mentia.ai.AiUnavailableException.Reason;

class AiBudgetGuardTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
    private static final LocalDate MONTH_START = LocalDate.of(2026, 11, 1);
    private static final UUID USER = UUID.randomUUID();
    private static final AiRequest SMALL = new AiRequest("sys", "Dame 50 palabras", 300);

    private final AiUsageRepository repository = mock(AiUsageRepository.class);
    private Clock clock = clockAt("2026-11-18T10:00:00Z");
    private AiBudgetGuard guard;

    @BeforeEach
    void setUp() {
        guard = newGuard(AiTestProperties.defaults());
    }

    private AiBudgetGuard newGuard(AiProperties properties) {
        return new AiBudgetGuard(properties, repository, new AiCostCalculator(properties.pricing()), clock);
    }

    private static Clock clockAt(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    @Test
    void sinGastoPrevioPermiteLaLlamada() {
        assertThatCode(() -> guard.check(SMALL, USER)).doesNotThrowAnyException();
    }

    @Test
    void bloqueaSiSePasaElMaxTokensPorLlamada() {
        assertLimit(new AiRequest(null, "hola", 1025), null);
    }

    @Test
    void bloqueaSiElPromptEsMasLargoQueElMaximo() {
        assertLimit(new AiRequest(null, "x".repeat(8001), 100), null);
    }

    @Test
    void bloqueaAlLlegarAlPresupuestoGlobalDiario() {
        when(repository.globalCostBetween(TODAY, TODAY)).thenReturn(2_000_000L);
        assertLimit(SMALL, null);
    }

    @Test
    void unPocoPorDebajoDelPresupuestoDiarioTodaviaPasa() {
        when(repository.globalCostBetween(TODAY, TODAY)).thenReturn(1_999_999L);
        assertThatCode(() -> guard.check(SMALL, null)).doesNotThrowAnyException();
    }

    @Test
    void bloqueaAlLlegarAlPresupuestoGlobalMensual() {
        when(repository.globalCostBetween(MONTH_START, TODAY)).thenReturn(20_000_000L);
        assertLimit(SMALL, null);
    }

    @Test
    void bloqueaAlLlegarAlMaximoDeLlamadasDiariasDelUsuario() {
        when(repository.userCallsOn(USER, TODAY)).thenReturn(5);
        assertLimit(SMALL, USER);
    }

    @Test
    void bloqueaAlLlegarAlTopeMensualDelUsuario() {
        when(repository.userCostBetween(USER, MONTH_START, TODAY)).thenReturn(100_000L);
        assertLimit(SMALL, USER);
    }

    @Test
    void loLimitesPorUsuarioNoAplicanAUsosGlobales() {
        when(repository.userCallsOn(any(), any())).thenReturn(99);
        when(repository.userCostBetween(any(), any(), any())).thenReturn(99_000_000L);
        assertThatCode(() -> guard.check(SMALL, null)).doesNotThrowAnyException();
    }

    @Test
    void unLimiteEnCeroActuaComoInterruptor() {
        AiBudgetGuard apagado = newGuard(AiTestProperties.with(AiTestProperties.limits("0", "20.00", "0.10", 5), 2));
        assertThatThrownBy(() -> apagado.check(SMALL, null))
                .isInstanceOfSatisfying(AiUnavailableException.class, e -> assertThat(e.reason()).isEqualTo(Reason.LIMIT_EXCEEDED));
    }

    @Test
    void alCambiarElDiaSeConsultaElDiaNuevo() {
        clock = clockAt("2026-11-19T00:00:01Z");
        guard = newGuard(AiTestProperties.defaults());
        // El gasto de "ayer" no cuenta: solo se consulta el 19.
        when(repository.globalCostBetween(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 18))).thenReturn(5_000_000L);

        assertThatCode(() -> guard.check(SMALL, null)).doesNotThrowAnyException();
        verify(repository).globalCostBetween(LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 19));
    }

    @Test
    void alCambiarElMesLaVentanaMensualArrancaDeNuevoElDiaUno() {
        clock = clockAt("2026-12-01T00:00:01Z");
        guard = newGuard(AiTestProperties.defaults());

        guard.check(SMALL, USER);

        // El 1° del mes la ventana mensual es de un solo día: arranca en cero, sin arrastrar noviembre.
        LocalDate dec1 = LocalDate.of(2026, 12, 1);
        verify(repository, atLeastOnce()).globalCostBetween(dec1, dec1);
        verify(repository).userCostBetween(USER, dec1, dec1);
        verify(repository, never()).globalCostBetween(eq(MONTH_START), any());
    }

    @Test
    void elDiaEsUtcAunqueElInstanteSeaTardeEnArgentina() {
        // 02:30 UTC del 19 = 23:30 del 18 en Buenos Aires: el día de cuenta es el 19 (UTC).
        clock = clockAt("2026-11-19T02:30:00Z");
        guard = newGuard(AiTestProperties.defaults());

        guard.check(SMALL, null);

        verify(repository).globalCostBetween(LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 19));
    }

    @Test
    void registraElCostoRealSoloCuandoLaLlamadaFueOk() {
        guard.record(AiPurpose.USER_FEEDBACK, 1000, 300, AiUsageResult.OK, USER);
        // 1000 × 0,10 + 300 × 0,50 = 250 micro-USD
        verify(repository).record(eq(TODAY), eq(clock.instant()), eq(AiPurpose.USER_FEEDBACK), eq("claude-haiku-5-5"),
                eq(1000), eq(300), eq(250L), eq(AiUsageResult.OK), eq(USER));

        guard.record(AiPurpose.USER_FEEDBACK, 0, 0, AiUsageResult.ERROR, USER);
        verify(repository).record(eq(TODAY), eq(clock.instant()), eq(AiPurpose.USER_FEEDBACK), eq("claude-haiku-5-5"),
                eq(0), eq(0), eq(0L), eq(AiUsageResult.ERROR), eq(USER));
    }

    private void assertLimit(AiRequest request, UUID userId) {
        assertThatThrownBy(() -> guard.check(request, userId))
                .isInstanceOfSatisfying(AiUnavailableException.class, e -> assertThat(e.reason()).isEqualTo(Reason.LIMIT_EXCEEDED));
    }
}
