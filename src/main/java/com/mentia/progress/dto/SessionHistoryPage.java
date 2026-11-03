package com.mentia.progress.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Schemas {@code SessionHistoryPage} y {@code SessionSummary} del contrato.
 */
public record SessionHistoryPage(List<Summary> items, int page, int size, long totalElements) {

    /** {@code accuracy} es null si la sesión no tiene intentos. */
    public record Summary(UUID id, String type, String status, Instant finishedAt, Double accuracy,
            int itemsCompleted, long totalTimeMs) {
    }
}
