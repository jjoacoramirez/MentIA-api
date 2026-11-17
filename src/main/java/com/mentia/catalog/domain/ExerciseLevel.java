package com.mentia.catalog.domain;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Parámetros de un ejercicio para un nivel de dificultad. La forma de {@code params} depende del tipo de ejercicio.
 */
@Entity
@Table(name = "exercise_levels")
public class ExerciseLevel {

    @EmbeddedId
    private Key id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", nullable = false)
    private Map<String, Object> params;

    @Column(name = "estimated_seconds", nullable = false)
    private int estimatedSeconds;

    @Column(name = "target_response_ms")
    private Integer targetResponseMs;

    protected ExerciseLevel() {
    }

    public UUID getExerciseId() {
        return id.exerciseId;
    }

    public int getLevel() {
        return id.level;
    }

    public Map<String, Object> getParams() {
        return Map.copyOf(params);
    }

    public int getEstimatedSeconds() {
        return estimatedSeconds;
    }

    /** Null si la velocidad no forma parte del desempeño en esta mecánica. */
    public Integer getTargetResponseMs() {
        return targetResponseMs;
    }

    @Embeddable
    public record Key(
            @Column(name = "exercise_id") UUID exerciseId,
            @Column(name = "level") short level) implements Serializable {
    }
}
