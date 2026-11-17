package com.mentia.catalog.dto;

import com.mentia.catalog.domain.ExerciseType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Schema {@code GenerateContentRequest}: qué mecánica y cuántas variantes nuevas pedir. */
public record GenerateContentRequest(@NotNull ExerciseType exerciseType, @Min(1) @Max(10) int count) {
}
