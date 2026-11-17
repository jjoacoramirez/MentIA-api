package com.mentia.session.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mentia.catalog.domain.ExerciseType;
import com.mentia.catalog.domain.Modality;
import com.mentia.session.domain.SessionStatus;
import com.mentia.session.domain.SessionType;

/**
 * Schema {@code Session} del contrato.
 */
public record SessionResponse(
        UUID id,
        SessionType type,
        SessionStatus status,
        int plannedMinutes,
        int estimatedDurationSeconds,
        List<Item> items,
        List<Map<String, Object>> rationale,
        Instant createdAt) {

    public record Item(
            UUID sessionItemId,
            int position,
            String exerciseCode,
            ExerciseType exerciseType,
            String category,
            Modality modality,
            int difficultyLevel,
            String variantKey,
            long seed,
            Map<String, Object> params,
            Map<String, Object> content,
            String instructions) {
    }
}
