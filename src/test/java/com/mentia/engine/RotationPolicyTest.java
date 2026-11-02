package com.mentia.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RotationPolicyTest {

    private static final RotationProperties DEFAULTS =
            new RotationProperties(5, true, List.of(0.0, 0.25, 0.5, 0.75, 1.0));

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();
    private static final List<UUID> CANDIDATES = List.of(A, B, C);

    private final RotationPolicy policy = new RotationPolicy(DEFAULTS, new Random(1));

    @ParameterizedTest(name = "usado hace {0} sesiones pesa {1}")
    @CsvSource({
            "1, 0.0",
            "2, 0.25",
            "3, 0.5",
            "4, 0.75",
            "5, 1.0",
            "6, 1.0",
            "0, 1.0"})
    void pesoPorRecencia(int sessionsAgo, double expected) {
        assertThat(policy.recencyWeight(sessionsAgo)).isEqualTo(expected);
    }

    @Test
    void loQueNuncaSeUsoPesaUno() {
        assertThat(policy.recencyWeight(null)).isEqualTo(1.0);
    }

    @Test
    void loUsadoEnLaSesionAnteriorNoSeRepiteSiHayAlternativas() {
        double[] weights = policy.weights(CANDIDATES, Function.identity(), Map.of(A, 1, B, 3));

        assertThat(weights).containsExactly(0.0, 0.5, 1.0);
        for (int i = 0; i < 500; i++) {
            assertThat(policy.pick(CANDIDATES, Function.identity(), Map.of(A, 1, B, 3))).isNotEqualTo(A);
        }
    }

    @Test
    void sinAvoidLastSessionSeRespetaElPesoConfigurado() {
        RotationProperties noAvoid = new RotationProperties(5, false, List.of(0.1, 0.25, 0.5, 0.75, 1.0));
        RotationPolicy lenient = new RotationPolicy(noAvoid, new Random(1));

        assertThat(lenient.weights(CANDIDATES, Function.identity(), Map.of(A, 1))).containsExactly(0.1, 1.0, 1.0);
    }

    @Test
    void conAvoidLastSessionSeEvitaLoAnteriorAunqueSuPesoConfiguradoNoSeaCero() {
        RotationProperties avoid = new RotationProperties(5, true, List.of(0.5, 0.5, 0.5, 0.5, 0.5));
        RotationPolicy strict = new RotationPolicy(avoid, new Random(1));

        assertThat(strict.weights(CANDIDATES, Function.identity(), Map.of(A, 1))).containsExactly(0.0, 1.0, 1.0);
    }

    @Test
    void siTodoSeUsoEnLaSesionAnteriorIgualSeEligeAlgo() {
        Map<UUID, Integer> allLast = Map.of(A, 1, B, 1, C, 1);

        Map<UUID, Integer> counts = count(policy, allLast, 3_000);

        assertThat(counts.keySet()).containsExactlyInAnyOrder(A, B, C);
    }

    @Test
    void conUnSoloCandidatoSiempreEsEse() {
        assertThat(policy.pick(List.of(A), Function.identity(), Map.of(A, 1))).isEqualTo(A);
    }

    @Test
    void elSorteoRespetaLosPesos() {
        // A pesa 0.25 (hace 2 sesiones), B 0.75 (hace 4), C 1.0 (nunca): proporciones 0.125, 0.375, 0.5.
        Map<UUID, Integer> counts = count(policy, Map.of(A, 2, B, 4), 20_000);

        assertThat(counts.get(A) / 20_000.0).isCloseTo(0.125, within(0.02));
        assertThat(counts.get(B) / 20_000.0).isCloseTo(0.375, within(0.02));
        assertThat(counts.get(C) / 20_000.0).isCloseTo(0.5, within(0.02));
    }

    @Test
    void conLaMismaSemillaElSorteoEsElMismo() {
        RotationPolicy first = new RotationPolicy(DEFAULTS, new Random(42));
        RotationPolicy second = new RotationPolicy(DEFAULTS, new Random(42));

        for (int i = 0; i < 50; i++) {
            assertThat(first.pick(CANDIDATES, Function.identity(), Map.of(B, 2)))
                    .isEqualTo(second.pick(CANDIDATES, Function.identity(), Map.of(B, 2)));
        }
    }

    @Test
    void sinCandidatosEsUnError() {
        assertThatThrownBy(() -> policy.pick(List.<UUID>of(), Function.identity(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laConfiguracionPideUnPesoPorSesion() {
        assertThat(DEFAULTS.isOneWeightPerSession()).isTrue();
        assertThat(new RotationProperties(3, true, List.of(0.0, 0.5)).isOneWeightPerSession()).isFalse();
    }

    private static Map<UUID, Integer> count(RotationPolicy policy, Map<UUID, Integer> usage, int draws) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (int i = 0; i < draws; i++) {
            counts.merge(policy.pick(CANDIDATES, Function.identity(), usage), 1, Integer::sum);
        }
        return counts;
    }
}
