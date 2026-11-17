package com.mentia.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentia.support.ApiClient;
import com.mentia.support.IntegrationTest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminMetricsIntegrationTest extends IntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ApiClient api;
    @Autowired Clock clock;
    @Autowired AdminMetricsService metrics;

    @Test
    void pairsOnlyCompletedTrainingInTheSameExerciseAndWindow() throws Exception {
        var player = api.newUser();
        var beginner = api.newUser();
        UUID category = jdbc.queryForObject("SELECT id FROM categories WHERE code = 'ATENCION'", UUID.class);
        UUID exercise = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        String code = "METRICS_" + exercise.toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
        jdbc.update("""
                INSERT INTO exercises(id, code, name, type, category_id, modality, instructions, active)
                VALUES (?, ?, 'Ejercicio de prueba', 'STROOP', ?, 'VISUAL', 'Probá el ejercicio', true)
                """, exercise, code, category);
        jdbc.update("""
                INSERT INTO exercise_variants(id, exercise_id, variant_key, content, active)
                VALUES (?, ?, 'test', '{}'::jsonb, true)
                """, variant, exercise);

        var now = OffsetDateTime.now(clock);
        save(player.id(), exercise, variant, category, now.minusDays(2), 2, "TRAINING", "COMPLETED");
        save(player.id(), exercise, variant, category, now.minusDays(1), 4, "TRAINING", "COMPLETED");
        save(player.id(), exercise, variant, category, now, 8, "TRAINING", "ABANDONED");
        save(player.id(), exercise, variant, category, now, 9, "INITIAL_ASSESSMENT", "COMPLETED");
        save(player.id(), exercise, variant, category, now.minusDays(8), 1, "TRAINING", "COMPLETED");
        save(beginner.id(), exercise, variant, category, now, 1, "TRAINING", "COMPLETED");

        var report = metrics.report(7, "ATENCION", code);
        assertThat(report.participants()).isEqualTo(2);
        assertThat(report.activity().stream().mapToLong(AdminMetricsService.Day::completed).sum()).isEqualTo(3);
        assertThat(report.activity().stream().mapToLong(AdminMetricsService.Day::abandoned).sum()).isEqualTo(1);
        assertThat(report.cohorts()).containsExactly(
                new AdminMetricsService.Cohort(1, 1, 0, 0, null),
                new AdminMetricsService.Cohort(2, 1, 1, 1, 2.0));
        assertThat(report.exercises()).singleElement().satisfies(row -> {
            assertThat(row.attempts()).isEqualTo(3);
            assertThat(row.medianMs()).isEqualTo(1200);
        });
    }

    @Test
    void summarizesAccountsHoursAgeRangesLevelsAndRetentionWithoutAdminAccounts() throws Exception {
        UUID category = jdbc.queryForObject("SELECT id FROM categories WHERE code = 'ATENCION'", UUID.class);
        UUID exercise = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO exercises(id, code, name, type, category_id, modality, instructions, active)
                VALUES (?, ?, 'Ejercicio de prueba', 'STROOP', ?, 'VISUAL', 'Probá el ejercicio', true)
                """, exercise, "OV_" + exercise.toString().replace("-", "").toUpperCase(java.util.Locale.ROOT),
                category);
        jdbc.update("""
                INSERT INTO exercise_variants(id, exercise_id, variant_key, content, active)
                VALUES (?, ?, 'test', '{}'::jsonb, true)
                """, variant, exercise);
        var before = metrics.report(30, "", "");

        var comesBack = api.newUser();
        var oneAndDone = api.newUser();
        var admin = api.newUser();
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.id());
        var now = OffsetDateTime.now(clock);
        save(comesBack.id(), exercise, variant, category, now.minusDays(3), 2, "TRAINING", "COMPLETED");
        save(comesBack.id(), exercise, variant, category, now.minusDays(2), 2, "TRAINING", "COMPLETED");
        save(oneAndDone.id(), exercise, variant, category, now.minusDays(3), 2, "TRAINING", "COMPLETED");
        // Una cuenta admin es de prueba: no entra en ninguna cifra.
        save(admin.id(), exercise, variant, category, now.minusDays(3), 2, "TRAINING", "COMPLETED");
        jdbc.update("""
                INSERT INTO performance_profiles(user_id, category_id, score, level, samples, updated_at)
                VALUES (?, ?, 60, 5, 3, ?)
                """, comesBack.id(), category, now);
        jdbc.update("""
                INSERT INTO performance_profiles(user_id, category_id, score, level, samples, updated_at)
                VALUES (?, ?, 10, 1, 1, ?)
                """, admin.id(), category, now);

        var after = metrics.report(30, "", "");

        assertThat(after.overview().registered() - before.overview().registered()).isEqualTo(2);
        assertThat(after.overview().newUsers() - before.overview().newUsers()).isEqualTo(2);
        assertThat(after.overview().trained() - before.overview().trained()).isEqualTo(2);
        assertThat(after.participants() - before.participants()).isEqualTo(2);
        assertThat(total(after.hours(), AdminMetricsService.Hour::started)
                - total(before.hours(), AdminMetricsService.Hour::started)).isEqualTo(3);
        assertThat(total(after.signups(), AdminMetricsService.Signup::users)
                - total(before.signups(), AdminMetricsService.Signup::users)).isEqualTo(2);
        assertThat(ageUsers(after, "18-24") - ageUsers(before, "18-24")).isEqualTo(2);
        assertThat(levelUsers(after, "ATENCION", 5) - levelUsers(before, "ATENCION", 5)).isEqualTo(1);
        // La cuenta admin (nivel 1) tampoco cuenta en los niveles.
        assertThat(levelUsers(after, "ATENCION", 1) - levelUsers(before, "ATENCION", 1)).isZero();
        // Los dos tuvieron tiempo de volver al día siguiente; uno volvió. Nadie cumplió todavía los 7 días.
        assertThat(after.retention().d1Eligible() - before.retention().d1Eligible()).isEqualTo(2);
        assertThat(after.retention().d1Returned() - before.retention().d1Returned()).isEqualTo(1);
        assertThat(after.retention().d7Eligible() - before.retention().d7Eligible()).isZero();
    }

    private static <T> long total(java.util.List<T> rows, java.util.function.ToLongFunction<T> value) {
        return rows.stream().mapToLong(value).sum();
    }

    private static long ageUsers(AdminMetricsService.Report report, String range) {
        return report.ageRanges().stream().filter(r -> r.ageRange().equals(range))
                .mapToLong(AdminMetricsService.AgeRange::users).sum();
    }

    private static long levelUsers(AdminMetricsService.Report report, String category, int level) {
        return report.levels().stream().filter(l -> l.category().equals(category) && l.level() == level)
                .mapToLong(AdminMetricsService.Level::users).sum();
    }

    private void save(UUID user, UUID exercise, UUID variant, UUID category,
            OffsetDateTime at, int level, String type, String status) {
        UUID session = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO training_sessions(id, user_id, type, status, planned_minutes, created_at, started_at, finished_at)
                VALUES (?, ?, ?, ?, 5, ?, ?, ?)
                """, session, user, type, status, at, at, at);
        jdbc.update("""
                INSERT INTO session_items(id, session_id, position, exercise_id, variant_id, category_id, difficulty_level, seed, params,
                    estimated_seconds)
                VALUES (?, ?, 1, ?, ?, ?, ?, 0, '{}'::jsonb, 30)
                """, item, session, exercise, variant, category, level);
        jdbc.update("""
                INSERT INTO attempts(id, client_attempt_id, session_item_id, session_id, user_id, exercise_id, variant_id,
                    category_id, difficulty_level, is_correct, accuracy, response_time_ms, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, true, 1, 1200, ?)
                """, UUID.randomUUID(), UUID.randomUUID(), item, session, user, exercise, variant, category, level, at);
    }
}
