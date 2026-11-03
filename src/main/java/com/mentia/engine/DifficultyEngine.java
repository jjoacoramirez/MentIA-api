package com.mentia.engine;

/**
 * Ajuste de nivel por categoría al cerrar una sesión (docs/plan-backend.md, sección 7). Reglas determinísticas,
 * sin IA; todos los umbrales vienen de configuración:
 * <ol>
 *   <li>Menos de {@code min-items-for-adaptation} intentos de la categoría: se mantiene.</li>
 *   <li>Precisión {@code >= accuracy-up} y tiempo dentro de la tolerancia (o sin tiempo objetivo): sube 1.</li>
 *   <li>Precisión {@code < accuracy-down}: baja 1 y la categoría queda reforzada.</li>
 *   <li>En cualquier otro caso: se mantiene.</li>
 * </ol>
 * El nivel siempre queda dentro de {@code [level-min, level-max]}.
 */
public class DifficultyEngine {

    private final AdaptationProperties adaptation;
    private final EngineProperties engine;

    public DifficultyEngine(AdaptationProperties adaptation, EngineProperties engine) {
        this.adaptation = adaptation;
        this.engine = engine;
    }

    public Decision nextLevel(int currentLevel, CategoryStats stats) {
        int level = engine.clamp(currentLevel);
        if (stats.items() < adaptation.minItemsForAdaptation()) {
            return keep(level, Reason.FEW_ITEMS, false);
        }
        if (stats.accuracy() >= adaptation.accuracyUp()) {
            if (stats.timeRatio() != null && stats.timeRatio() > adaptation.responseTimeTolerance()) {
                return keep(level, Reason.SLOW_RESPONSE, false);
            }
            return level >= engine.levelMax()
                    ? keep(level, Reason.AT_MAX, false)
                    : new Decision(level + 1, Adjustment.UP, Reason.HIGH_PERFORMANCE, false);
        }
        if (stats.accuracy() < adaptation.accuracyDown()) {
            return level <= engine.levelMin()
                    ? keep(level, Reason.AT_MIN, true)
                    : new Decision(level - 1, Adjustment.DOWN, Reason.LOW_PERFORMANCE, true);
        }
        return keep(level, Reason.STEADY, false);
    }

    private static Decision keep(int level, Reason reason, boolean reinforce) {
        return new Decision(level, Adjustment.KEEP, reason, reinforce);
    }

    /**
     * @param items     intentos de la categoría en la sesión
     * @param accuracy  precisión promedio (0 a 1)
     * @param timeRatio tiempo de respuesta promedio sobre el tiempo objetivo, o null si ningún ejercicio lo define
     */
    public record CategoryStats(int items, double accuracy, Double timeRatio) {
    }

    /** {@code reinforce}: la categoría tuvo precisión baja y conviene darle más lugar en las próximas sesiones. */
    public record Decision(int level, Adjustment adjustment, Reason reason, boolean reinforce) {
    }

    public enum Adjustment {
        UP,
        KEEP,
        DOWN
    }

    public enum Reason {
        FEW_ITEMS,
        HIGH_PERFORMANCE,
        SLOW_RESPONSE,
        LOW_PERFORMANCE,
        STEADY,
        AT_MAX,
        AT_MIN
    }
}
