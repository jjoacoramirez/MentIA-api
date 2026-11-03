package com.mentia.gamification.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_achievements")
public class UserAchievement {

    @EmbeddedId
    private Key id;

    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt;

    @Column(name = "session_id")
    private UUID sessionId;

    protected UserAchievement() {
    }

    public UserAchievement(UUID userId, String achievementCode, Instant unlockedAt, UUID sessionId) {
        this.id = new Key(userId, achievementCode);
        this.unlockedAt = unlockedAt;
        this.sessionId = sessionId;
    }

    public String getAchievementCode() {
        return id.achievementCode();
    }

    public Instant getUnlockedAt() {
        return unlockedAt;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    @Embeddable
    public record Key(
            @Column(name = "user_id") UUID userId,
            @Column(name = "achievement_code") String achievementCode) implements Serializable {
    }
}
