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
                SELECT s.id, s.user_id, s.status,
                    CAST(s.started_at AT TIME ZONE 'America/Argentina/Buenos_Aires' AS date) AS day
                FROM training_sessions s
                WHERE s.type = 'TRAINING' AND s.started_at >= :start AND s.started_at < :end
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
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public AdminMetricsService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Report report(int days, String category, String exercise) {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        LocalDate start = today.minusDays(days - 1L);
        Map<String, Object> filters = Map.of(
                "start", start.atStartOfDay(ZONE).toOffsetDateTime(),
                "end", today.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime(),
                "category", category, "exercise", exercise);
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
                    AVG(a.accuracy) AS accuracy,
                    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY a.response_time_ms) AS median_ms
                FROM scoped s JOIN attempts a ON a.session_id = s.id
                JOIN exercises e ON e.id = a.exercise_id JOIN categories c ON c.id = a.category_id
                WHERE s.status = 'COMPLETED'
                  AND (:category = '' OR c.code = :category)
                  AND (:exercise = '' OR e.code = :exercise)
                GROUP BY e.code, e.name, c.code ORDER BY c.code, e.name
                """, filters, (rs, n) -> new Exercise(rs.getString("code"), rs.getString("name"),
                    rs.getString("category"), rs.getLong("attempts"), rs.getDouble("accuracy"), rs.getDouble("median_ms")));
        List<Option> options = jdbc.query("""
                SELECT e.code, e.name, c.code AS category, c.name AS category_name
                FROM exercises e JOIN categories c ON c.id = e.category_id
                WHERE e.active OR EXISTS (SELECT 1 FROM session_items i WHERE i.exercise_id = e.id)
                ORDER BY c.name, e.name
                """, Map.of(), (rs, n) -> new Option(rs.getString("code"), rs.getString("name"),
                    rs.getString("category"), rs.getString("category_name")));
        return new Report(start, today, participants, activity, cohorts, exercises, options);
    }

    public record Report(LocalDate from, LocalDate to, long participants, List<Day> activity,
            List<Cohort> cohorts, List<Exercise> exercises, List<Option> options) {}
    public record Day(LocalDate day, long active, long started, long completed, long abandoned) {}
    public record Cohort(int bucket, long participants, long comparable, long improved, Double meanChange) {}
    public record Exercise(String code, String name, String category, long attempts, double accuracy, double medianMs) {}
    public record Option(String code, String name, String category, String categoryName) {}
}
