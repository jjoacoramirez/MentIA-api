package com.mentia.session.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "training_sessions")
public class TrainingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private SessionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatus status;

    @Column(name = "planned_minutes", nullable = false)
    private short plannedMinutes;

    // Motivos de la personalización (BACK-09). Vacío en la evaluación inicial.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rationale", nullable = false)
    private List<Map<String, Object>> rationale = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    // Snapshot del resultado al cerrar la sesión (BACK-13).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result")
    private Map<String, Object> result;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<SessionItem> items = new ArrayList<>();

    protected TrainingSession() {
    }

    public static TrainingSession generate(UUID userId, SessionType type, int plannedMinutes,
            List<Map<String, Object>> rationale, Instant createdAt) {
        TrainingSession session = new TrainingSession();
        session.userId = userId;
        session.type = type;
        session.status = SessionStatus.GENERATED;
        session.plannedMinutes = (short) plannedMinutes;
        session.rationale = new ArrayList<>(rationale);
        session.createdAt = createdAt;
        return session;
    }

    /** Agrega un ítem al final; la posición se asigna en orden (1, 2, 3...). */
    public SessionItem addItem(PlannedItem planned) {
        requireStatus(SessionStatus.GENERATED);
        SessionItem item = new SessionItem(this, items.size() + 1, planned);
        items.add(item);
        return item;
    }

    /** {@code GENERATED → IN_PROGRESS}. */
    public void start(Instant now) {
        requireStatus(SessionStatus.GENERATED);
        status = SessionStatus.IN_PROGRESS;
        startedAt = now;
    }

    /** {@code IN_PROGRESS → COMPLETED}, guardando el snapshot del resultado. */
    public void complete(Instant now, Map<String, Object> result) {
        requireStatus(SessionStatus.IN_PROGRESS);
        status = SessionStatus.COMPLETED;
        finishedAt = now;
        // No Map.copyOf: el snapshot puede tener valores null (por ejemplo, comparison sin sesión previa).
        this.result = new LinkedHashMap<>(result);
    }

    /** Reemplaza el snapshot de una sesión completada (por ejemplo, para sumarle las recompensas). */
    public void replaceResult(Map<String, Object> result) {
        requireStatus(SessionStatus.COMPLETED);
        this.result = new LinkedHashMap<>(result);
    }

    /** Una sesión abierta ({@code GENERATED} o {@code IN_PROGRESS}) pasa a {@code ABANDONED}. */
    public void abandon(Instant now) {
        if (!isOpen()) {
            throw new IllegalStateException("La sesión " + id + " ya está cerrada (" + status + ")");
        }
        status = SessionStatus.ABANDONED;
        finishedAt = now;
    }

    public boolean isOpen() {
        return status == SessionStatus.GENERATED || status == SessionStatus.IN_PROGRESS;
    }

    /** Abierta y creada hace más de {@code staleAfter}: ya no se retoma, se abandona. */
    public boolean isStale(Instant now, Duration staleAfter) {
        return isOpen() && createdAt.plus(staleAfter).isBefore(now);
    }

    public boolean isOwnedBy(UUID userId) {
        return this.userId.equals(userId);
    }

    public Optional<SessionItem> item(UUID itemId) {
        return items.stream().filter(i -> i.getId().equals(itemId)).findFirst();
    }

    private void requireStatus(SessionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("La sesión " + id + " está en " + status + ", se esperaba " + expected);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public SessionType getType() {
        return type;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public int getPlannedMinutes() {
        return plannedMinutes;
    }

    public List<Map<String, Object>> getRationale() {
        return List.copyOf(rationale);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public List<SessionItem> getItems() {
        return List.copyOf(items);
    }
}
