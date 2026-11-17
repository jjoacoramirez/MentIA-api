package com.mentia.gamification.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Lo que ganó el usuario en una sesión. Una fila por sesión: el premio no se otorga dos veces. */
@Entity
@Table(name = "session_rewards")
public class SessionReward {

    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "xp_earned", nullable = false)
    private int xpEarned;

    @Column(name = "xp_total", nullable = false)
    private int xpTotal;

    @Column(name = "level", nullable = false)
    private int level;

    @Column(name = "leveled_up", nullable = false)
    private boolean leveledUp;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SessionReward() {
    }

    public SessionReward(UUID sessionId, UUID userId, int xpEarned, int xpTotal, int level, boolean leveledUp,
            Instant createdAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.xpEarned = xpEarned;
        this.xpTotal = xpTotal;
        this.level = level;
        this.leveledUp = leveledUp;
        this.createdAt = createdAt;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getXpEarned() {
        return xpEarned;
    }

    public int getXpTotal() {
        return xpTotal;
    }

    public int getLevel() {
        return level;
    }

    public boolean isLeveledUp() {
        return leveledUp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
