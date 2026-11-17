package com.mentia.gamification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AchievementRulesTest {

    private static final AchievementRules.Stats STATS = new AchievementRules.Stats(5, 2, 3);

    @ParameterizedTest(name = "{0} >= {1} → {2}")
    @CsvSource({
            "sessions_completed, 5, true",
            "sessions_completed, 6, false",
            "level_reached, 2, true",
            "level_reached, 3, false",
            "categories_practiced, 3, true",
            "categories_practiced, 4, false",
            "tipo_inventado, 0, false"})
    void evaluaCadaTipoDeCondicion(String type, int min, boolean expected) {
        assertThat(AchievementRules.isMet(Map.of("type", type, "min", min), STATS)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "sessions_completed, true",
            "level_reached, true",
            "categories_practiced, true",
            "racha, false"})
    void reconoceLosTiposSoportados(String type, boolean known) {
        assertThat(AchievementRules.isKnown(Map.of("type", type))).isEqualTo(known);
    }
}
