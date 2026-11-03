package com.mentia.session.domain;

import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Un ejercicio dentro de una sesión. {@code params} es una copia de los del nivel al generar la sesión,
 * así un cambio posterior en el catálogo no altera una sesión ya generada.
 */
@Entity
@Table(name = "session_items")
public class SessionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private TrainingSession session;

    @Column(name = "position", nullable = false)
    private short position;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "difficulty_level", nullable = false)
    private short difficultyLevel;

    @Column(name = "seed", nullable = false)
    private long seed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", nullable = false)
    private Map<String, Object> params;

    protected SessionItem() {
    }

    SessionItem(TrainingSession session, int position, UUID exerciseId, UUID variantId, UUID categoryId,
            int difficultyLevel, long seed, Map<String, Object> params) {
        this.session = session;
        this.position = (short) position;
        this.exerciseId = exerciseId;
        this.variantId = variantId;
        this.categoryId = categoryId;
        this.difficultyLevel = (short) difficultyLevel;
        this.seed = seed;
        this.params = Map.copyOf(params);
    }

    public UUID getId() {
        return id;
    }

    public TrainingSession getSession() {
        return session;
    }

    public int getPosition() {
        return position;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    public UUID getVariantId() {
        return variantId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public int getDifficultyLevel() {
        return difficultyLevel;
    }

    public long getSeed() {
        return seed;
    }

    public Map<String, Object> getParams() {
        return Map.copyOf(params);
    }
}
