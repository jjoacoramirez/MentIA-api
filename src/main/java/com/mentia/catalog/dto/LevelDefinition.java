package com.mentia.catalog.dto;

import java.util.Map;

import com.mentia.catalog.domain.ExerciseLevel;

/**
 * Parámetros de un ejercicio en un nivel. {@code targetResponseMs} es null si la velocidad no cuenta.
 */
public record LevelDefinition(int level, Map<String, Object> params, int estimatedSeconds, Integer targetResponseMs) {

    public static LevelDefinition from(ExerciseLevel level) {
        return new LevelDefinition(level.getLevel(), level.getParams(), level.getEstimatedSeconds(),
                level.getTargetResponseMs());
    }
}
