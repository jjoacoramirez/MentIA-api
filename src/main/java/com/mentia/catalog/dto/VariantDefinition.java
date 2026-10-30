package com.mentia.catalog.dto;

import java.util.Map;
import java.util.UUID;

import com.mentia.catalog.domain.ExerciseVariant;

public record VariantDefinition(UUID id, UUID exerciseId, String variantKey, Map<String, Object> content) {

    public static VariantDefinition from(ExerciseVariant variant) {
        return new VariantDefinition(variant.getId(), variant.getExerciseId(), variant.getVariantKey(),
                variant.getContent());
    }
}
