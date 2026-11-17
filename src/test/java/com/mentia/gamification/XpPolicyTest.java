package com.mentia.gamification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.mentia.gamification.dto.GamificationState;

class XpPolicyTest {

    private final XpPolicy policy = new XpPolicy(new GamificationProperties(10, 2, 5, 0.8, 100));

    @ParameterizedTest(name = "{0} ítems con precisión {1} → {2} XP")
    @CsvSource({
            "6, 0.80, 27",
            "6, 0.79, 22",
            "0, 1.00, 10",
            "10, 1.00, 35"})
    void xpPorSesion(int items, double accuracy, int expected) {
        assertThat(policy.xpForSession(items, accuracy)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} XP → nivel {1}")
    @CsvSource({
            "0, 1",
            "99, 1",
            "100, 2",
            "299, 2",
            "300, 3",
            "600, 4"})
    void nivelSegunXpAcumulada(int xp, int level) {
        assertThat(policy.levelFor(xp)).isEqualTo(level);
    }

    @Test
    void estadoDentroDelNivel() {
        // Nivel 2 arranca en 100 XP y para el 3 hacen falta 200 más.
        assertThat(policy.state(150)).isEqualTo(new GamificationState(150, 2, 50, 200));
        assertThat(policy.state(0)).isEqualTo(new GamificationState(0, 1, 0, 100));
    }
}
