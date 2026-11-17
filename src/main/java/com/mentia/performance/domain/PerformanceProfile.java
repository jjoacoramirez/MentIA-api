package com.mentia.performance.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Desempeño del usuario dentro de MentIA en una categoría: score de 0 a 100, nivel de entrenamiento y
 * progreso dentro del nivel.
 * No es una medición de capacidades.
 */
@Entity
@Table(name = "performance_profiles")
public class PerformanceProfile {

    @EmbeddedId
    private Key id;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "level", nullable = false)
    private short level;

    @Column(name = "level_progress", nullable = false)
    private short levelProgress;

    @Column(name = "samples", nullable = false)
    private int samples;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PerformanceProfile() {
    }

    public PerformanceProfile(UUID userId, UUID categoryId, double score, int level, int levelProgress, int samples,
            Instant now) {
        this.id = new Key(userId, categoryId);
        reset(score, level, levelProgress, samples, now);
    }

    /** Reemplaza el punto de partida (evaluación inicial o valores por defecto). */
    public void reset(double score, int level, int levelProgress, int samples, Instant now) {
        this.score = score;
        this.level = (short) level;
        this.levelProgress = (short) levelProgress;
        this.samples = samples;
        this.updatedAt = now;
    }

    /**
     * Suma una sesión: el score se suaviza con media móvil exponencial; el nivel y el progreso son los que decidió
     * el motor.
     */
    public void recordSession(double sessionScore, int items, int newLevel, int newProgress, double emaAlpha,
            Instant now) {
        this.score = score * (1 - emaAlpha) + sessionScore * emaAlpha;
        this.samples += items;
        this.level = (short) newLevel;
        this.levelProgress = (short) newProgress;
        this.updatedAt = now;
    }

    public UUID getUserId() {
        return id.userId();
    }

    public UUID getCategoryId() {
        return id.categoryId();
    }

    public double getScore() {
        return score;
    }

    public int getLevel() {
        return level;
    }

    /** Progreso dentro del nivel: 0 es el piso y 100 el techo. */
    public int getLevelProgress() {
        return levelProgress;
    }

    public int getSamples() {
        return samples;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Embeddable
    public record Key(
            @Column(name = "user_id") UUID userId,
            @Column(name = "category_id") UUID categoryId) implements Serializable {
    }
}
