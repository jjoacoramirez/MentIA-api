package com.mentia.metrics;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Modelo de lectura de las métricas internas: agregaciones en SQL sobre usuarios, sesiones, intentos y desempeño.
 * Solo lee. Devuelve datos por usuario identificados por el UUID interno; nunca salen así del módulo.
 */
@Repository
class MetricsQueries {

    private final JdbcTemplate jdbc;

    MetricsQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Sesiones de entrenamiento cerradas, días activos y precisión de la primera y la última sesión por usuario. */
    List<UserActivity> userActivity(ZoneId zone, boolean includeAdmins) {
        return jdbc.query("""
                WITH closed AS (
                    SELECT ts.user_id, ts.status, ts.finished_at,
                           (SELECT avg(a.accuracy) FROM attempts a WHERE a.session_id = ts.id) AS accuracy
                    FROM training_sessions ts
                    WHERE ts.type = 'TRAINING' AND ts.status IN ('COMPLETED', 'ABANDONED')
                )
                SELECT u.id,
                       count(c.status) FILTER (WHERE c.status = 'COMPLETED') AS completed,
                       count(c.status) FILTER (WHERE c.status = 'ABANDONED') AS abandoned,
                       count(DISTINCT (c.finished_at AT TIME ZONE ?)::date)
                           FILTER (WHERE c.status = 'COMPLETED') AS active_days,
                       count(c.accuracy) FILTER (WHERE c.status = 'COMPLETED') AS with_accuracy,
                       (array_agg(c.accuracy ORDER BY c.finished_at)
                           FILTER (WHERE c.status = 'COMPLETED' AND c.accuracy IS NOT NULL))[1] AS first_accuracy,
                       (array_agg(c.accuracy ORDER BY c.finished_at DESC)
                           FILTER (WHERE c.status = 'COMPLETED' AND c.accuracy IS NOT NULL))[1] AS last_accuracy,
                       EXISTS (SELECT 1 FROM performance_profiles p WHERE p.user_id = u.id) AS has_starting_point
                FROM users u
                LEFT JOIN closed c ON c.user_id = u.id
                WHERE ? OR u.role = 'USER'
                GROUP BY u.id""",
                (rs, i) -> new UserActivity(
                        rs.getObject("id", UUID.class),
                        rs.getInt("completed"),
                        rs.getInt("abandoned"),
                        rs.getInt("active_days"),
                        rs.getInt("with_accuracy"),
                        (Double) rs.getObject("first_accuracy"),
                        (Double) rs.getObject("last_accuracy"),
                        rs.getBoolean("has_starting_point")),
                zone.getId(), includeAdmins);
    }

    /**
     * Por usuario y categoría: el nivel con el que jugó su primera sesión de entrenamiento completada en esa
     * categoría y el nivel actual. Solo categorías en las que completó al menos una sesión.
     */
    List<LevelChange> levelChanges(boolean includeAdmins) {
        return jdbc.query("""
                SELECT * FROM (
                    SELECT p.user_id, c.code AS category, p.level AS current_level,
                           (SELECT si.difficulty_level
                            FROM session_items si
                            JOIN training_sessions ts ON ts.id = si.session_id
                            WHERE ts.user_id = p.user_id AND ts.type = 'TRAINING' AND ts.status = 'COMPLETED'
                              AND si.category_id = p.category_id
                            ORDER BY ts.finished_at, si.position
                            LIMIT 1) AS start_level
                    FROM performance_profiles p
                    JOIN categories c ON c.id = p.category_id
                    JOIN users u ON u.id = p.user_id
                    WHERE ? OR u.role = 'USER'
                ) levels
                WHERE start_level IS NOT NULL""",
                (rs, i) -> new LevelChange(
                        rs.getObject("user_id", UUID.class),
                        rs.getString("category"),
                        rs.getInt("start_level"),
                        rs.getInt("current_level")),
                includeAdmins);
    }

    record UserActivity(UUID userId, int completed, int abandoned, int activeDays, int sessionsWithAccuracy,
            Double firstAccuracy, Double lastAccuracy, boolean hasStartingPoint) {
    }

    record LevelChange(UUID userId, String category, int startLevel, int currentLevel) {
    }
}
