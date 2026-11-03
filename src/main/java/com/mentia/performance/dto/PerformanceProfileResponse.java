package com.mentia.performance.dto;

import java.time.Instant;
import java.util.List;

/**
 * Schema {@code PerformanceProfile} del contrato: desempeño dentro de MentIA, no una medición de capacidades.
 */
public record PerformanceProfileResponse(List<Entry> categories) {

    public record Entry(String category, double score, int level, Instant updatedAt) {
    }
}
