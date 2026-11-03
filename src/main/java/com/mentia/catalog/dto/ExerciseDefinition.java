package com.mentia.catalog.dto;

import java.util.UUID;

import com.mentia.catalog.domain.Exercise;
import com.mentia.catalog.domain.ExerciseType;
import com.mentia.catalog.domain.Modality;

/**
 * Vista de un ejercicio para otros módulos (sesiones, motor), sin exponer la entidad JPA.
 */
public record ExerciseDefinition(
        UUID id,
        String code,
        String name,
        ExerciseType type,
        UUID categoryId,
        String categoryCode,
        Modality modality,
        String instructions) {

    public static ExerciseDefinition from(Exercise exercise) {
        return new ExerciseDefinition(exercise.getId(), exercise.getCode(), exercise.getName(), exercise.getType(),
                exercise.getCategory().getId(), exercise.getCategory().getCode(), exercise.getModality(),
                exercise.getInstructions());
    }
}
