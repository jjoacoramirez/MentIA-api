package com.mentia.attempt.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.mentia.session.dto.AttemptTarget;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Resultado de un ítem de sesión, tal como lo reporta el cliente (el servidor no lo recalcula).
 */
@Entity
@Table(name = "attempts")
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_attempt_id", nullable = false)
    private UUID clientAttemptId;

    @Column(name = "session_item_id", nullable = false)
    private UUID sessionItemId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "difficulty_level", nullable = false)
    private short difficultyLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response")
    private Map<String, Object> response;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "accuracy", nullable = false)
    private double accuracy;

    @Column(name = "response_time_ms", nullable = false)
    private int responseTimeMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics")
    private Map<String, Object> metrics;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Attempt() {
    }

    public static Attempt record(UUID userId, UUID clientAttemptId, AttemptTarget target, Map<String, Object> response,
            boolean correct, double accuracy, int responseTimeMs, Map<String, Object> metrics, Instant now) {
        Attempt attempt = new Attempt();
        attempt.userId = userId;
        attempt.clientAttemptId = clientAttemptId;
        attempt.sessionId = target.sessionId();
        attempt.sessionItemId = target.sessionItemId();
        attempt.exerciseId = target.exerciseId();
        attempt.variantId = target.variantId();
        attempt.categoryId = target.categoryId();
        attempt.difficultyLevel = (short) target.difficultyLevel();
        attempt.response = response;
        attempt.correct = correct;
        attempt.accuracy = accuracy;
        attempt.responseTimeMs = responseTimeMs;
        attempt.metrics = metrics;
        attempt.createdAt = now;
        return attempt;
    }

    /** Un reintento con el mismo clientAttemptId tiene que ser para el mismo ítem de la misma sesión. */
    public boolean isRetryOf(UUID sessionId, UUID sessionItemId) {
        return this.sessionId.equals(sessionId) && this.sessionItemId.equals(sessionItemId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientAttemptId() {
        return clientAttemptId;
    }

    public UUID getSessionItemId() {
        return sessionItemId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getUserId() {
        return userId;
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

    public Map<String, Object> getResponse() {
        return response;
    }

    public boolean isCorrect() {
        return correct;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public int getResponseTimeMs() {
        return responseTimeMs;
    }

    public Map<String, Object> getMetrics() {
        return metrics;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
