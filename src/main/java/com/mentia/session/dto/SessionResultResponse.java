package com.mentia.session.dto;

import java.util.List;
import java.util.UUID;

import com.mentia.session.domain.SessionStatus;
import com.mentia.session.domain.SessionType;

/**
 * Schema {@code SessionResult} del contrato. {@code comparison} y {@code gamification} llegan con BACK-13 y BACK-18.
 */
public record SessionResultResponse(
        UUID sessionId,
        SessionType type,
        SessionStatus status,
        int itemsTotal,
        int itemsCompleted,
        int correctCount,
        int errorCount,
        double accuracy,
        long totalTimeMs,
        List<CategoryResult> categories,
        List<Adaptation> adaptations,
        Object comparison,
        Object gamification) {

    public record CategoryResult(
            String category,
            int itemsCompleted,
            double accuracy,
            Integer avgResponseTimeMs,
            int difficultyLevelBefore,
            int difficultyLevelAfter) {
    }

    public record Adaptation(String category, int from, int to, Direction direction) {

        public static Adaptation between(String category, int from, int to) {
            Direction direction = to > from ? Direction.UP : to < from ? Direction.DOWN : Direction.KEEP;
            return new Adaptation(category, from, to, direction);
        }
    }

    public enum Direction {
        UP,
        KEEP,
        DOWN
    }
}
