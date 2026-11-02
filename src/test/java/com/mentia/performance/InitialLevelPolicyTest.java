package com.mentia.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.mentia.engine.EngineProperties;
import com.mentia.performance.AssessmentProperties.Threshold;

class InitialLevelPolicyTest {

    // Los mismos valores que application.yml (sección 7 del plan), en desorden a propósito.
    private static final AssessmentProperties ASSESSMENT = new AssessmentProperties(2, List.of(
            new Threshold(0.60, 3),
            new Threshold(0.90, 5),
            new Threshold(0.00, 1),
            new Threshold(0.75, 4),
            new Threshold(0.40, 2)));
    private static final EngineProperties ENGINE = new EngineProperties(3, 1, 10);

    private final InitialLevelPolicy policy = new InitialLevelPolicy(ASSESSMENT, ENGINE);

    @ParameterizedTest(name = "precisión {0} → nivel {1}")
    @CsvSource({
            "1.00, 5",
            "0.90, 5",
            "0.8999, 4",
            "0.75, 4",
            "0.74, 3",
            "0.60, 3",
            "0.59, 2",
            "0.40, 2",
            "0.39, 1",
            "0.00, 1"})
    void nivelInicialSegunLaTablaDeLaSeccion7(double accuracy, int expected) {
        assertThat(policy.levelFor(accuracy)).isEqualTo(expected);
    }

    @Test
    void elNivelSeAcotaAlRangoConfigurado() {
        AssessmentProperties generous = new AssessmentProperties(2, List.of(new Threshold(0.0, 15)));

        assertThat(new InitialLevelPolicy(generous, ENGINE).levelFor(0.5)).isEqualTo(10);
    }

    @ParameterizedTest
    @CsvSource({
            "0.0, 0.0",
            "0.5, 50.0",
            "1.0, 100.0"})
    void elScoreInicialEsCienPorLaPrecision(double accuracy, double score) {
        assertThat(policy.scoreFor(accuracy)).isEqualTo(score);
    }

    @Test
    void laConfiguracionExigeUnUmbralEnCero() {
        assertThat(ASSESSMENT.isCoveringZero()).isTrue();
        assertThat(new AssessmentProperties(2, List.of(new Threshold(0.5, 2))).isCoveringZero()).isFalse();
    }
}
