package com.mentia.progress;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mentia.progress.dto.SessionHistoryPage;

/**
 * Modelo de lectura del progreso: agregaciones en SQL sobre sesiones e intentos (no se cargan entidades en memoria).
 * Solo lee; las escrituras siguen pasando por los servicios de cada módulo.
 */
@Repository
class ProgressQueries {

    private final JdbcTemplate jdbc;

    ProgressQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<SessionHistoryPage.Summary> sessions(UUID userId, int page, int size) {
        return jdbc.query("""
                SELECT s.id, s.type, s.status, s.finished_at,
                       avg(a.accuracy) AS accuracy, count(a.id) AS items,
                       coalesce(sum(a.response_time_ms), 0) AS total_ms
                FROM training_sessions s
                LEFT JOIN attempts a ON a.session_id = s.id
                WHERE s.user_id = ?
                GROUP BY s.id
                ORDER BY s.created_at DESC, s.id
                LIMIT ? OFFSET ?""",
                (rs, i) -> new SessionHistoryPage.Summary(
                        rs.getObject("id", UUID.class),
                        rs.getString("type"),
                        rs.getString("status"),
                        instant(rs.getTimestamp("finished_at")),
                        (Double) rs.getObject("accuracy"),
                        rs.getInt("items"),
                        rs.getLong("total_ms")),
                userId, size, (long) page * size);
    }

    long countSessions(UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM training_sessions WHERE user_id = ?", Long.class, userId);
    }

    int countByStatus(UUID userId, String status) {
        return jdbc.queryForObject("SELECT count(*) FROM training_sessions WHERE user_id = ? AND status = ?",
                Integer.class, userId, status);
    }

    Instant lastCompletedAt(UUID userId) {
        return instant(jdbc.queryForObject(
                "SELECT max(finished_at) FROM training_sessions WHERE user_id = ? AND status = 'COMPLETED'",
                Timestamp.class, userId));
    }

    /** Días (en la zona indicada) con al menos una sesión completada, desde {@code from} inclusive. */
    List<LocalDate> activeDays(UUID userId, ZoneId zone, LocalDate from) {
        return jdbc.queryForList("""
                SELECT DISTINCT (finished_at AT TIME ZONE ?)::date AS day
                FROM training_sessions
                WHERE user_id = ? AND status = 'COMPLETED' AND (finished_at AT TIME ZONE ?)::date >= ?
                ORDER BY day DESC""", LocalDate.class, zone.getId(), userId, zone.getId(), from);
    }

    int completedOn(UUID userId, ZoneId zone, LocalDate day) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM training_sessions
                WHERE user_id = ? AND status = 'COMPLETED' AND (finished_at AT TIME ZONE ?)::date = ?""",
                Integer.class, userId, zone.getId(), day);
    }

    /** Filas por categoría y período, de sesiones completadas, en orden de categoría y fecha. */
    List<CategoryPeriod> categoryPeriods(UUID userId, ZoneId zone, String granularity, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT c.code AS category,
                       date_trunc(?, a.created_at AT TIME ZONE ?)::date AS period_start,
                       avg(a.accuracy) AS accuracy,
                       avg(a.difficulty_level) AS avg_level,
                       count(DISTINCT a.session_id) AS sessions
                FROM attempts a
                JOIN training_sessions s ON s.id = a.session_id
                JOIN categories c ON c.id = a.category_id
                WHERE a.user_id = ? AND s.status = 'COMPLETED'
                  AND (a.created_at AT TIME ZONE ?)::date BETWEEN ? AND ?
                GROUP BY 1, 2
                ORDER BY 1, 2""",
                (rs, i) -> new CategoryPeriod(
                        rs.getString("category"),
                        rs.getObject("period_start", LocalDate.class),
                        rs.getDouble("accuracy"),
                        rs.getDouble("avg_level"),
                        rs.getInt("sessions")),
                granularity, zone.getId(), userId, zone.getId(), from, to);
    }

    record CategoryPeriod(String category, LocalDate periodStart, double accuracy, double avgLevel, int sessions) {
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
