package com.mentia.session.dto;

import java.util.List;
import java.util.UUID;

import com.mentia.session.domain.SessionStatus;
import com.mentia.session.domain.SessionType;

/**
 * Schema {@code SessionResult} del contrato. La comparación es solo contra la sesión anterior del mismo usuario
 * y del mismo tipo, y habla de desempeño dentro de MentIA.
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
        Comparison comparison,
        Rewards gamification) {

    public SessionResultResponse withGamification(Rewards rewards) {
        return new SessionResultResponse(sessionId, type, status, itemsTotal, itemsCompleted, correctCount, errorCount,
                accuracy, totalTimeMs, categories, adaptations, comparison, rewards);
    }

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

    public record Comparison(double previousAccuracy, double accuracyDelta) {
    }

    public record Rewards(int xpEarned, int xpTotal, int level, boolean leveledUp,
            List<AchievementRef> unlockedAchievements) {
    }

    public record AchievementRef(String code, String name) {
    }
}
