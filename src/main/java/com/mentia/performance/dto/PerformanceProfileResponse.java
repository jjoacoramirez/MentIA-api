package com.mentia.performance.dto;

import java.time.Instant;
import java.util.List;

/**
 * Schema {@code PerformanceProfile} del contrato: desempeño dentro de MentIA, no una medición de capacidades.
 */
public record PerformanceProfileResponse(List<Entry> categories) {

    /** {@code progress}: puntos dentro del nivel, de 0 (piso) a 100 (techo). */
    public record Entry(String category, double score, int level, int progress, Instant updatedAt) {
    }
}
