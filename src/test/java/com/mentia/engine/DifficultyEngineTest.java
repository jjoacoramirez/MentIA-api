package com.mentia.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.mentia.engine.DifficultyEngine.Adjustment;
import com.mentia.engine.DifficultyEngine.CategoryStats;
import com.mentia.engine.DifficultyEngine.Decision;
import com.mentia.engine.DifficultyEngine.Reason;

class DifficultyEngineTest {

    private static final DifficultyEngine ENGINE = new DifficultyEngine(
            new AdaptationProperties(0.85, 0.60, 1.25, 2),
            new EngineProperties(3, 1, 10, 0.3));

    @ParameterizedTest(name = "nivel {0}, {1} ítems, precisión {2}, tiempo {3} → {4} ({5} → {6})")
    @CsvSource(nullValues = "-", value = {
            // Pocas muestras: se mantiene aunque el desempeño sea extremo.
            "5, 1, 1.00, -,    KEEP, FEW_ITEMS,        5, false",
            "5, 1, 0.00, -,    KEEP, FEW_ITEMS,        5, false",
            // Desempeño alto.
            "5, 2, 0.85, -,    UP,   HIGH_PERFORMANCE, 6, false",
            "5, 4, 1.00, 1.00, UP,   HIGH_PERFORMANCE, 6, false",
            "5, 4, 1.00, 1.25, UP,   HIGH_PERFORMANCE, 6, false",
            "5, 4, 1.00, 1.26, KEEP, SLOW_RESPONSE,    5, false",
            "10, 4, 1.00, -,   KEEP, AT_MAX,           10, false",
            // Desempeño medio.
            "5, 4, 0.84, -,    KEEP, STEADY,           5, false",
            "5, 4, 0.60, -,    KEEP, STEADY,           5, false",
            // Desempeño bajo: baja y se refuerza la categoría.
            "5, 4, 0.59, -,    DOWN, LOW_PERFORMANCE,  4, true",
            "5, 4, 0.59, 3.00, DOWN, LOW_PERFORMANCE,  4, true",
            "1, 4, 0.10, -,    KEEP, AT_MIN,           1, true"})
    void reglasDeLaSeccion7(int current, int items, double accuracy, Double timeRatio, Adjustment adjustment,
            Reason reason, int expectedLevel, boolean reinforce) {
        Decision decision = ENGINE.nextLevel(current, new CategoryStats(items, accuracy, timeRatio));

        assertThat(decision).isEqualTo(new Decision(expectedLevel, adjustment, reason, reinforce));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1",
            "12, 10"})
    void unNivelFueraDeRangoSeAcotaAntesDeDecidir(int current, int expected) {
        assertThat(ENGINE.nextLevel(current, new CategoryStats(4, 0.7, null)).level()).isEqualTo(expected);
    }
}
