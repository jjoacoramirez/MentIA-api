package com.mentia.engine;

import java.util.List;

/**
 * Ajuste de nivel por categoría al cerrar una sesión (docs/plan-backend.md, sección 7). Reglas determinísticas,
 * sin IA; todos los umbrales vienen de configuración.
 * <p>
 * Dentro de cada nivel hay un progreso de 0 (piso) a {@link AdaptationProperties#MAX} (techo). Cada actividad
 * de la categoría suma o resta puntos:
 * <ol>
 *   <li>Precisión {@code >= accuracy-up} y tiempo dentro de la tolerancia (o sin tiempo objetivo): {@code points-up}.</li>
 *   <li>Precisión {@code >= accuracy-up} pero más lenta: {@code points-up-slow}.</li>
 *   <li>Precisión {@code < accuracy-down}: resta {@code points-down}.</li>
 *   <li>En cualquier otro caso: no cambia.</li>
 * </ol>
 * Si el progreso llega al techo sube un nivel; si llega a 0, baja uno. Al cambiar de nivel el progreso vuelve a
 * {@code start}, así que una sesión mueve como mucho un nivel. En el nivel máximo (o mínimo) el progreso queda
 * en el techo (o en el piso).
 */
public class DifficultyEngine {

    private final AdaptationProperties adaptation;
    private final EngineProperties engine;

    public DifficultyEngine(AdaptationProperties adaptation, EngineProperties engine) {
        this.adaptation = adaptation;
        this.engine = engine;
    }

    public Decision next(int currentLevel, int currentProgress, List<Activity> activities) {
        int level = engine.clamp(currentLevel);
        int points = activities.stream().mapToInt(this::points).sum();
        int progress = Math.max(0, Math.min(AdaptationProperties.MAX, currentProgress)) + points;
        int start = adaptation.levelProgress().start();

        if (progress >= AdaptationProperties.MAX) {
            return level < engine.levelMax()
                    ? new Decision(level + 1, start, points, Adjustment.UP)
                    : new Decision(level, AdaptationProperties.MAX, points, Adjustment.KEEP);
        }
        if (progress <= 0) {
            return level > engine.levelMin()
                    ? new Decision(level - 1, start, points, Adjustment.DOWN)
                    : new Decision(level, 0, points, Adjustment.KEEP);
        }
        return new Decision(level, progress, points, Adjustment.KEEP);
    }

    /** Puntos que suma (o resta) una actividad. */
    public int points(Activity activity) {
        AdaptationProperties.LevelProgress values = adaptation.levelProgress();
        if (activity.accuracy() >= adaptation.accuracyUp()) {
            boolean slow = activity.timeRatio() != null && activity.timeRatio() > adaptation.responseTimeTolerance();
            return slow ? values.pointsUpSlow() : values.pointsUp();
        }
        return activity.accuracy() < adaptation.accuracyDown() ? -values.pointsDown() : 0;
    }

    /**
     * @param accuracy  precisión de la actividad (0 a 1)
     * @param timeRatio tiempo de respuesta sobre el tiempo objetivo, o null si el ejercicio no lo define
     */
    public record Activity(double accuracy, Double timeRatio) {
    }

    /** Nivel y progreso con los que sigue la categoría, y los puntos que sumó la sesión. */
    public record Decision(int level, int progress, int points, Adjustment adjustment) {
    }

    public enum Adjustment {
        UP,
        KEEP,
        DOWN
    }
}
