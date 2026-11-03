package com.mentia.gamification.dto;

import java.time.Instant;

/** Schema {@code Achievement} del contrato. {@code unlockedAt} es null si todavía no se obtuvo. */
public record AchievementResponse(String code, String name, String description, boolean unlocked, Instant unlockedAt) {
}
