package com.mentia.performance;

import java.util.Comparator;
import java.util.List;

import com.mentia.engine.EngineProperties;

/**
 * Nivel inicial por categoría a partir de la precisión promedio en la evaluación (sección 7 del plan):
 * se toma el umbral más alto alcanzado y el resultado se acota a {@code [level-min, level-max]}.
 * Función pura: todos los umbrales vienen de configuración.
 */
public class InitialLevelPolicy {

    private final List<AssessmentProperties.Threshold> thresholds;
    private final EngineProperties engine;

    public InitialLevelPolicy(AssessmentProperties assessment, EngineProperties engine) {
        this.thresholds = assessment.initialLevels().stream()
                .sorted(Comparator.comparingDouble(AssessmentProperties.Threshold::minAccuracy).reversed())
                .toList();
        this.engine = engine;
    }

    public int levelFor(double accuracy) {
        return thresholds.stream()
                .filter(t -> accuracy >= t.minAccuracy())
                .findFirst()
                .map(t -> engine.clamp(t.level()))
                .orElse(engine.levelMin());
    }

    /** El score inicial es {@code 100 × precisión}, acotado a [0, 100]. */
    public double scoreFor(double accuracy) {
        return Math.max(0.0, Math.min(100.0, 100.0 * accuracy));
    }
}
