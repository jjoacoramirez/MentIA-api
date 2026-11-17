package com.mentia.feedback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Los datos del resultado de una sesión que alcanzan para escribir el comentario: solo números y nombres de áreas.
 * Nada de ids, alias ni fechas, así que lo que sale hacia la IA no identifica a nadie.
 */
record FeedbackFacts(boolean assessment, int itemsCompleted, int itemsTotal, int accuracyPct, Integer deltaPoints,
        List<Area> areas) {

    /** {@code levelBefore} es null en la evaluación inicial. */
    record Area(String name, int accuracyPct, Integer levelBefore, int levelAfter) {

        boolean rose() {
            return levelBefore != null && levelAfter > levelBefore;
        }
    }

    /**
     * @param result      el snapshot guardado al completar la sesión
     * @param areaNames   nombre para mostrar de cada categoría, por código
     */
    static FeedbackFacts from(Map<String, Object> result, Map<String, String> areaNames) {
        boolean assessment = "INITIAL_ASSESSMENT".equals(result.get("type"));
        List<Area> areas = new ArrayList<>();
        if (result.get("categories") instanceof List<?> categories) {
            for (Object item : categories) {
                if (item instanceof Map<?, ?> category) {
                    String code = String.valueOf(category.get("category"));
                    Integer before = assessment ? null : integer(category.get("difficultyLevelBefore"));
                    Integer after = integer(category.get("difficultyLevelAfter"));
                    areas.add(new Area(areaNames.getOrDefault(code, "una de las áreas"),
                            percent(category.get("accuracy")), before, after == null ? 0 : after));
                }
            }
        }
        Integer delta = null;
        if (result.get("comparison") instanceof Map<?, ?> comparison && comparison.get("accuracyDelta") instanceof Number n) {
            delta = (int) Math.round(n.doubleValue() * 100);
        }
        return new FeedbackFacts(assessment, intOr(result.get("itemsCompleted")), intOr(result.get("itemsTotal")),
                percent(result.get("accuracy")), delta, List.copyOf(areas));
    }

    private static int percent(Object value) {
        return value instanceof Number n ? (int) Math.round(Math.max(0, Math.min(1, n.doubleValue())) * 100) : 0;
    }

    private static Integer integer(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private static int intOr(Object value) {
        Integer number = integer(value);
        return number == null ? 0 : number;
    }
}
