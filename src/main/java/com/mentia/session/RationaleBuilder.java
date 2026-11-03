package com.mentia.session;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Motivos de la personalización que muestra el front (FRONT-22). Texto en es-AR y sin lenguaje clínico:
 * se habla de actividades y niveles dentro de MentIA, nunca de capacidades.
 */
final class RationaleBuilder {

    static final String FOCUS_LOWER_PERFORMANCE = "FOCUS_LOWER_PERFORMANCE";
    static final String LEVEL_UP = "LEVEL_UP";
    static final String LEVEL_DOWN = "LEVEL_DOWN";
    static final String VARIETY_ROTATION = "VARIETY_ROTATION";

    private RationaleBuilder() {
    }

    /**
     * @param itemsByCategory cantidad de ítems de cada categoría en la sesión generada
     * @param scoreByCategory score actual de cada categoría
     * @param nameByCategory  nombre visible de cada categoría
     * @param lastAdaptations dirección del último ajuste de nivel por categoría ({@code UP}, {@code KEEP}, {@code DOWN})
     */
    static List<Map<String, Object>> build(Map<String, Integer> itemsByCategory, Map<String, Double> scoreByCategory,
            Map<String, String> nameByCategory, Map<String, String> lastAdaptations) {
        List<Map<String, Object>> rationale = new ArrayList<>();

        lowestScoreWithMoreItems(itemsByCategory, scoreByCategory).ifPresent(code -> rationale.add(entry(
                FOCUS_LOWER_PERFORMANCE, code, "Hoy sumamos más actividades de " + name(nameByCategory, code)
                        + " porque es donde más desafío tuviste en tus últimas sesiones.")));

        lastAdaptations.forEach((code, direction) -> {
            if (!itemsByCategory.containsKey(code)) {
                return;
            }
            if ("UP".equals(direction)) {
                rationale.add(entry(LEVEL_UP, code, "Subimos un nivel en " + name(nameByCategory, code)
                        + " porque te fue muy bien la última vez."));
            } else if ("DOWN".equals(direction)) {
                rationale.add(entry(LEVEL_DOWN, code, "Bajamos un nivel en " + name(nameByCategory, code)
                        + " para que vuelvas a tomar ritmo."));
            }
        });

        rationale.add(entry(VARIETY_ROTATION, null,
                "Rotamos ejercicios y estímulos para que cada sesión sea distinta."));
        return rationale;
    }

    /** La categoría de menor score, solo si recibió más ítems que cualquier otra (si no, no hubo foco real). */
    private static Optional<String> lowestScoreWithMoreItems(Map<String, Integer> items,
            Map<String, Double> scores) {
        if (items.size() < 2) {
            return Optional.empty();
        }
        String lowest = items.keySet().stream()
                .min(Comparator.comparingDouble(code -> scores.getOrDefault(code, 0.0)))
                .orElseThrow();
        int lowestItems = items.get(lowest);
        boolean mostItems = items.entrySet().stream()
                .filter(e -> !e.getKey().equals(lowest))
                .allMatch(e -> e.getValue() < lowestItems);
        return mostItems ? Optional.of(lowest) : Optional.empty();
    }

    private static String name(Map<String, String> names, String code) {
        return names.getOrDefault(code, code).toLowerCase(Locale.ROOT);
    }

    private static Map<String, Object> entry(String code, String category, String message) {
        // LinkedHashMap y no Map.of: category puede ser null.
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("code", code);
        entry.put("category", category);
        entry.put("message", message);
        return entry;
    }
}
