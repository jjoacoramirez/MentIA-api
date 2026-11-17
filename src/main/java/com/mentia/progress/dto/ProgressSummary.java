package com.mentia.progress.dto;

import java.time.Instant;

import com.mentia.gamification.dto.GamificationState;

/**
 * Schema {@code ProgressSummary} del contrato. {@code currentStreakDays} y {@code dailyGoal} son null si
 * no están habilitados.
 */
public record ProgressSummary(
        int sessionsCompleted,
        int sessionsAbandoned,
        int activeDaysLast30,
        Instant lastSessionAt,
        Integer currentStreakDays,
        DailyGoal dailyGoal,
        GamificationState gamification) {

    public record DailyGoal(int target, int completedToday) {
    }
}
