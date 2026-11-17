package com.mentia.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RationaleBuilderTest {

    private static final Map<String, String> NAMES = Map.of(
            "ATENCION", "Atención", "MEMORIA_VISUAL", "Memoria visual", "MEMORIA_TRABAJO", "Memoria de trabajo");

    @Test
    void explicaElFocoEnLaCategoriaDeMenorDesempenoSiRecibioMasItems() {
        List<Map<String, Object>> rationale = RationaleBuilder.build(
                ordered("ATENCION", 2, "MEMORIA_VISUAL", 2, "MEMORIA_TRABAJO", 4),
                Map.of("ATENCION", 80.0, "MEMORIA_VISUAL", 75.0, "MEMORIA_TRABAJO", 40.0), NAMES, Map.of());

        assertThat(rationale.getFirst())
                .containsEntry("code", "FOCUS_LOWER_PERFORMANCE")
                .containsEntry("category", "MEMORIA_TRABAJO");
        assertThat((String) rationale.getFirst().get("message")).contains("memoria de trabajo");
        assertThat(rationale.getLast()).containsEntry("code", "VARIETY_ROTATION").containsEntry("category", null);
    }

    @Test
    void sinFocoRealNoLoMenciona() {
        List<Map<String, Object>> rationale = RationaleBuilder.build(
                ordered("ATENCION", 3, "MEMORIA_VISUAL", 3, "MEMORIA_TRABAJO", 3),
                Map.of("ATENCION", 80.0, "MEMORIA_VISUAL", 75.0, "MEMORIA_TRABAJO", 40.0), NAMES, Map.of());

        assertThat(rationale).extracting(e -> e.get("code")).containsExactly("VARIETY_ROTATION");
    }

    @Test
    void explicaLosCambiosDeNivelDeLaUltimaSesion() {
        List<Map<String, Object>> rationale = RationaleBuilder.build(
                ordered("ATENCION", 3, "MEMORIA_VISUAL", 3),
                Map.of("ATENCION", 80.0, "MEMORIA_VISUAL", 75.0), NAMES,
                ordered("ATENCION", "UP", "MEMORIA_VISUAL", "DOWN", "MEMORIA_TRABAJO", "UP"));

        assertThat(rationale).extracting(e -> e.get("code") + ":" + e.get("category")).containsExactly(
                "LEVEL_UP:ATENCION", "LEVEL_DOWN:MEMORIA_VISUAL", "VARIETY_ROTATION:null");
    }

    @SuppressWarnings("unchecked")
    private static <V> Map<String, V> ordered(Object... keysAndValues) {
        Map<String, V> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], (V) keysAndValues[i + 1]);
        }
        return map;
    }
}
