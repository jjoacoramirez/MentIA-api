package com.mentia.metrics;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminMetricsService {
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String SCOPE = """
            WITH scoped AS (
                SELECT s.id, s.user_id, s.status, s.started_at,
                    CAST(s.started_at AT TIME ZONE 'America/Argentina/Buenos_Aires' AS date) AS day
                FROM training_sessions s
                WHERE s.type = 'TRAINING' AND s.started_at >= :start AND s.started_at < :end
                  AND (:includeAdmins OR EXISTS (SELECT 1 FROM users u WHERE u.id = s.user_id AND u.role = 'USER'))
                  AND EXISTS (
                    SELECT 1 FROM session_items i
                    JOIN categories c ON c.id = i.category_id
                    JOIN exercises e ON e.id = i.exercise_id
                    WHERE i.session_id = s.id
                      AND (:category = '' OR c.code = :category)
                      AND (:exercise = '' OR e.code = :exercise)
                  )
            )
            """;
    private static final String COHORTS = SCOPE + """
            , activity AS (
                SELECT user_id, COUNT(DISTINCT day) AS days
                FROM scoped WHERE status = 'COMPLETED' GROUP BY user_id
            ), per_session AS (
                SELECT s.user_id, a.exercise_id, s.id, s.day,
                    MIN(a.created_at) AS attempted_at, AVG(a.difficulty_level) AS level
                FROM scoped s
                JOIN attempts a ON a.session_id = s.id
                JOIN categories c ON c.id = a.category_id
                JOIN exercises e ON e.id = a.exercise_id
                WHERE s.status = 'COMPLETED'
                  AND (:category = '' OR c.code = :category)
                  AND (:exercise = '' OR e.code = :exercise)
                GROUP BY s.user_id, a.exercise_id, s.id, s.day
            ), ordered AS (
                SELECT *,
                    FIRST_VALUE(level) OVER w AS first_level,
                    LAST_VALUE(level) OVER w AS last_level,
                    ROW_NUMBER() OVER w AS position,
                    COUNT(*) OVER (PARTITION BY user_id, exercise_id) AS sessions
                FROM per_session
                WINDOW w AS (PARTITION BY user_id, exercise_id ORDER BY attempted_at, id
                    ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)
            ), changes AS (
                SELECT user_id, AVG(last_level - first_level) AS delta
                FROM ordered WHERE position = 1 AND sessions >= 2 GROUP BY user_id
            )
            SELECT CASE WHEN days = 1 THEN 1 WHEN days <= 3 THEN 2 ELSE 4 END AS bucket,
                COUNT(*) AS participants, COUNT(delta) AS comparable,
                COUNT(*) FILTER (WHERE delta > 0) AS improved, CAST(AVG(delta) AS double precision) AS mean_change
            FROM activity LEFT JOIN changes USING (user_id)
            GROUP BY bucket ORDER BY bucket
            """;
    private static final String ZONE_ID = "America/Argentina/Buenos_Aires";
    /** Cuentas que entran en las cifras: las admin son de prueba salvo mentia.metrics.include-admins. */
    private static final String REAL_USER = "(:includeAdmins OR u.role = 'USER')";
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final boolean includeAdmins;

    public AdminMetricsService(NamedParameterJdbcTemplate jdbc, Clock clock, MetricsProperties properties) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.includeAdmins = properties.includeAdmins();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Report report(int days, String category, String exercise) {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        LocalDate start = today.minusDays(days - 1L);
        Map<String, Object> filters = Map.of(
                "start", start.atStartOfDay(ZONE).toOffsetDateTime(),
                "end", today.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime(),
                "category", category, "exercise", exercise, "includeAdmins", includeAdmins);
        List<Day> activity = jdbc.query(SCOPE + """
                SELECT day, COUNT(DISTINCT user_id) AS active, COUNT(*) AS started,
                    COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed,
                    COUNT(*) FILTER (WHERE status = 'ABANDONED') AS abandoned
                FROM scoped GROUP BY day ORDER BY day
                """, filters, (rs, n) -> new Day(rs.getObject("day", LocalDate.class),
                    rs.getLong("active"), rs.getLong("started"), rs.getLong("completed"), rs.getLong("abandoned")));
        long participants = jdbc.queryForObject(SCOPE + "SELECT COUNT(DISTINCT user_id) FROM scoped", filters, Long.class);
        List<Cohort> cohorts = jdbc.query(COHORTS, filters, (rs, n) -> new Cohort(rs.getInt("bucket"),
                rs.getLong("participants"), rs.getLong("comparable"), rs.getLong("improved"),
                rs.getObject("mean_change", Double.class)));
        List<Exercise> exercises = jdbc.query(SCOPE + """
                SELECT e.code, e.name, c.code AS category, COUNT(*) AS attempts,
                    COUNT(DISTINCT s.user_id) AS players, AVG(a.difficulty_level) AS avg_level,
                    AVG(a.accuracy) AS accuracy,
                    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY a.response_time_ms) AS median_ms
                FROM scoped s JOIN attempts a ON a.session_id = s.id
                JOIN exercises e ON e.id = a.exercise_id JOIN categories c ON c.id = a.category_id
                WHERE s.status = 'COMPLETED'
                  AND (:category = '' OR c.code = :category)
                  AND (:exercise = '' OR e.code = :exercise)
                GROUP BY e.code, e.name, c.code ORDER BY c.code, e.name
                """, filters, (rs, n) -> new Exercise(rs.getString("code"), rs.getString("name"),
                    rs.getString("category"), rs.getLong("attempts"), rs.getLong("players"), rs.getDouble("avg_level"),
                    rs.getDouble("accuracy"), rs.getDouble("median_ms")));
        List<Option> options = jdbc.query("""
                SELECT e.code, e.name, c.code AS category, c.name AS category_name
                FROM exercises e JOIN categories c ON c.id = e.category_id
                WHERE e.active OR EXISTS (SELECT 1 FROM session_items i WHERE i.exercise_id = e.id)
                ORDER BY c.name, e.name
                """, Map.of(), (rs, n) -> new Option(rs.getString("code"), rs.getString("name"),
                    rs.getString("category"), rs.getString("category_name")));
        return new Report(start, today, participants, activity, cohorts, exercises, options,
                overview(filters), signups(filters), hours(filters), ageRanges(filters), levels(filters),
                retention(filters, today, start));
    }

    private Overview overview(Map<String, Object> filters) {
        return jdbc.queryForObject(SCOPE + """
                SELECT
                    (SELECT COUNT(*) FROM users u WHERE %1$s) AS registered,
                    (SELECT COUNT(*) FROM users u WHERE %1$s
                        AND u.created_at >= :start AND u.created_at < :end) AS new_users,
                    (SELECT COUNT(DISTINCT p.user_id) FROM performance_profiles p
                        JOIN users u ON u.id = p.user_id WHERE %1$s) AS assessed,
                    (SELECT COUNT(DISTINCT t.user_id) FROM training_sessions t JOIN users u ON u.id = t.user_id
                        WHERE t.type = 'TRAINING' AND t.status = 'COMPLETED' AND %1$s) AS trained,
                    (SELECT COUNT(DISTINCT ps.user_id) FROM push_subscriptions ps
                        JOIN users u ON u.id = ps.user_id WHERE %1$s) AS reminders,
                    (SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (t.finished_at - t.started_at)))
                        FROM training_sessions t JOIN scoped s ON s.id = t.id
                        WHERE s.status = 'COMPLETED' AND t.finished_at > t.started_at) AS median_seconds
                """.formatted(REAL_USER), filters, (rs, n) -> new Overview(rs.getLong("registered"),
                rs.getLong("new_users"), rs.getLong("assessed"), rs.getLong("trained"), rs.getLong("reminders"),
                rs.getObject("median_seconds", Double.class)));
    }

    private List<Signup> signups(Map<String, Object> filters) {
        return jdbc.query("""
                SELECT CAST(u.created_at AT TIME ZONE '%s' AS date) AS day, COUNT(*) AS users
                FROM users u WHERE %s AND u.created_at >= :start AND u.created_at < :end
                GROUP BY day ORDER BY day
                """.formatted(ZONE_ID, REAL_USER), filters,
                (rs, n) -> new Signup(rs.getObject("day", LocalDate.class), rs.getLong("users")));
    }

    private List<Hour> hours(Map<String, Object> filters) {
        return jdbc.query(SCOPE + """
                SELECT CAST(EXTRACT(HOUR FROM started_at AT TIME ZONE '%s') AS int) AS hour,
                    COUNT(*) AS started, COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed
                FROM scoped GROUP BY hour ORDER BY hour
                """.formatted(ZONE_ID), filters,
                (rs, n) -> new Hour(rs.getInt("hour"), rs.getLong("started"), rs.getLong("completed")));
    }

    private List<AgeRange> ageRanges(Map<String, Object> filters) {
        return jdbc.query(SCOPE + """
                SELECT tp.age_range, COUNT(*) AS users,
                    COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM scoped s WHERE s.user_id = u.id)) AS active
                FROM users u JOIN training_profiles tp ON tp.user_id = u.id
                WHERE %s GROUP BY tp.age_range ORDER BY tp.age_range
                """.formatted(REAL_USER), filters,
                (rs, n) -> new AgeRange(rs.getString("age_range"), rs.getLong("users"), rs.getLong("active")));
    }

    /** Cuántas personas están hoy en cada nivel de cada categoría. Sin identificadores. */
    private List<Level> levels(Map<String, Object> filters) {
        return jdbc.query("""
                SELECT c.code AS category, p.level, COUNT(*) AS users
                FROM performance_profiles p JOIN categories c ON c.id = p.category_id
                JOIN users u ON u.id = p.user_id
                WHERE %s AND (:category = '' OR c.code = :category)
                GROUP BY c.code, p.level ORDER BY c.code, p.level
                """.formatted(REAL_USER), filters,
                (rs, n) -> new Level(rs.getString("category"), rs.getInt("level"), rs.getLong("users")));
    }

    /**
     * De quienes completaron su primer entrenamiento en el período, cuántos volvieron al día siguiente y dentro de los
     * 7 días. Solo cuenta a quien ya tuvo tiempo de volver. No usa los filtros de categoría ni ejercicio.
     */
    private Retention retention(Map<String, Object> filters, LocalDate today, LocalDate start) {
        Map<String, Object> params = new java.util.HashMap<>(filters);
        params.put("today", java.sql.Date.valueOf(today));
        params.put("startDay", java.sql.Date.valueOf(start));
        return jdbc.queryForObject("""
                WITH days AS (
                    SELECT DISTINCT t.user_id, CAST(t.finished_at AT TIME ZONE '%1$s' AS date) AS day
                    FROM training_sessions t JOIN users u ON u.id = t.user_id
                    WHERE t.type = 'TRAINING' AND t.status = 'COMPLETED' AND t.finished_at IS NOT NULL AND %2$s
                ), first AS (
                    SELECT user_id, MIN(day) AS first_day FROM days GROUP BY user_id
                )
                SELECT
                    COUNT(*) FILTER (WHERE f.first_day <= CAST(:today AS date) - 1) AS d1_eligible,
                    COUNT(*) FILTER (WHERE f.first_day <= CAST(:today AS date) - 1 AND EXISTS (
                        SELECT 1 FROM days d WHERE d.user_id = f.user_id AND d.day = f.first_day + 1)) AS d1_returned,
                    COUNT(*) FILTER (WHERE f.first_day <= CAST(:today AS date) - 7) AS d7_eligible,
                    COUNT(*) FILTER (WHERE f.first_day <= CAST(:today AS date) - 7 AND EXISTS (
                        SELECT 1 FROM days d WHERE d.user_id = f.user_id
                          AND d.day BETWEEN f.first_day + 1 AND f.first_day + 7)) AS d7_returned
                FROM first f WHERE f.first_day >= CAST(:startDay AS date)
                """.formatted(ZONE_ID, REAL_USER), params,
                (rs, n) -> new Retention(rs.getLong("d1_eligible"), rs.getLong("d1_returned"),
                    rs.getLong("d7_eligible"), rs.getLong("d7_returned")));
    }

    public record Report(LocalDate from, LocalDate to, long participants, List<Day> activity,
            List<Cohort> cohorts, List<Exercise> exercises, List<Option> options, Overview overview,
            List<Signup> signups, List<Hour> hours, List<AgeRange> ageRanges, List<Level> levels,
            Retention retention) {}
    /** Totales de cuentas (no dependen del período salvo newUsers) y mediana de duración de una sesión. */
    public record Overview(long registered, long newUsers, long assessed, long trained, long reminderUsers,
            Double medianSessionSeconds) {}
    public record Signup(LocalDate day, long users) {}
    public record Hour(int hour, long started, long completed) {}
    public record AgeRange(String ageRange, long users, long active) {}
    public record Level(String category, int level, long users) {}
    public record Retention(long d1Eligible, long d1Returned, long d7Eligible, long d7Returned) {}
    public record Day(LocalDate day, long active, long started, long completed, long abandoned) {}
    public record Cohort(int bucket, long participants, long comparable, long improved, Double meanChange) {}
    public record Exercise(String code, String name, String category, long attempts, long players, double avgLevel,
            double accuracy, double medianMs) {}
    public record Option(String code, String name, String category, String categoryName) {}
}
