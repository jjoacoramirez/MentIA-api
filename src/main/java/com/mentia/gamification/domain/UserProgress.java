package com.mentia.gamification.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_progress")
public class UserProgress {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "xp", nullable = false)
    private int xp;

    @Column(name = "level", nullable = false)
    private int level;

    @Column(name = "sessions_completed", nullable = false)
    private int sessionsCompleted;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProgress() {
    }

    public UserProgress(UUID userId, Instant now) {
        this.userId = userId;
        this.level = 1;
        this.updatedAt = now;
    }

    public void addSession(int xpEarned, int newLevel, Instant now) {
        this.xp += xpEarned;
        this.level = newLevel;
        this.sessionsCompleted++;
        this.updatedAt = now;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getXp() {
        return xp;
    }

    public int getLevel() {
        return level;
    }

    public int getSessionsCompleted() {
        return sessionsCompleted;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
