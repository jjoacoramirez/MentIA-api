package com.mentia.performance.dto;

import java.time.Instant;
import java.util.UUID;

import com.mentia.performance.domain.PerformanceProfile;

/** Score y nivel actuales de una categoría, para otros módulos (generación de sesiones, progreso). */
public record PerformanceSnapshot(UUID categoryId, double score, int level, int samples, Instant updatedAt) {

    public static PerformanceSnapshot from(PerformanceProfile profile) {
        return new PerformanceSnapshot(profile.getCategoryId(), profile.getScore(), profile.getLevel(),
                profile.getSamples(), profile.getUpdatedAt());
    }
}
