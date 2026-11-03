package com.mentia.gamification;

import java.util.Map;

/**
 * Evalúa la condición de un logro (función pura). Tipos soportados:
 * <ul>
 *   <li>{@code sessions_completed}: al menos {@code min} sesiones completadas.</li>
 *   <li>{@code level_reached}: nivel general de MentIA al menos {@code min}.</li>
 *   <li>{@code categories_practiced}: actividades completadas en al menos {@code min} categorías distintas.</li>
 * </ul>
 * Un tipo desconocido nunca se cumple: sumar un tipo nuevo requiere código acá.
 */
final class AchievementRules {

    private AchievementRules() {
    }

    record Stats(int sessionsCompleted, int level, int categoriesPracticed) {
    }

    static boolean isMet(Map<String, Object> condition, Stats stats) {
        Object min = condition.get("min");
        if (!(min instanceof Number threshold)) {
            return false;
        }
        int value = switch (String.valueOf(condition.get("type"))) {
            case "sessions_completed" -> stats.sessionsCompleted();
            case "level_reached" -> stats.level();
            case "categories_practiced" -> stats.categoriesPracticed();
            default -> Integer.MIN_VALUE;
        };
        return value >= threshold.intValue();
    }

    static boolean isKnown(Map<String, Object> condition) {
        return switch (String.valueOf(condition.get("type"))) {
            case "sessions_completed", "level_reached", "categories_practiced" -> true;
            default -> false;
        };
    }
}
