package com.mentia.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mentia.catalog.CatalogService;
import com.mentia.catalog.VariantSelector;
import com.mentia.catalog.dto.ExerciseDefinition;
import com.mentia.catalog.dto.VariantDefinition;
import com.mentia.session.dto.RecentUsage;
import com.mentia.support.IntegrationTest;

class SessionHistoryIntegrationTest extends IntegrationTest {

    @Autowired
    SessionHistoryService history;

    @Autowired
    CatalogService catalog;

    @Autowired
    VariantSelector selector;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void sinSesionesNoHayUsoReciente() {
        assertThat(history.recentUsage(newUser(), 5)).isEqualTo(RecentUsage.none());
    }

    @Test
    void cuentaHaceCuantasSesionesSeUsoCadaVarianteYEjercicio() {
        UUID user = newUser();
        ExerciseDefinition nBack = catalog.activeExercises("MEMORIA_TRABAJO").stream()
                .filter(e -> e.code().equals("N_BACK")).findFirst().orElseThrow();
        List<VariantDefinition> variants = catalog.activeVariants(nBack.id());
        Instant base = Instant.parse("2026-11-01T10:00:00Z");

        // De la más vieja a la más nueva: v0, v1, v0. La más nueva es "hace 1 sesión".
        session(user, nBack, variants.get(0), base);
        session(user, nBack, variants.get(1), base.plusSeconds(60));
        session(user, nBack, variants.get(0), base.plusSeconds(120));

        RecentUsage usage = history.recentUsage(user, 5);

        assertThat(usage.variantsSessionsAgo()).containsExactlyInAnyOrderEntriesOf(Map.of(
                variants.get(0).id(), 1,
                variants.get(1).id(), 2));
        assertThat(usage.exercisesSessionsAgo()).containsExactlyEntriesOf(Map.of(nBack.id(), 1));
        assertThat(history.recentUsage(user, 1).variantsSessionsAgo()).containsOnlyKeys(variants.get(0).id());
    }

    @Test
    void elSelectorNoRepiteLaVarianteDeLaSesionAnterior() {
        UUID user = newUser();
        ExerciseDefinition nBack = catalog.activeExercises("MEMORIA_TRABAJO").stream()
                .filter(e -> e.code().equals("N_BACK")).findFirst().orElseThrow();
        VariantDefinition last = catalog.activeVariants(nBack.id()).getFirst();
        session(user, nBack, last, Instant.now());

        for (int i = 0; i < 30; i++) {
            assertThat(selector.pick(user, nBack.id(), 3).id()).isNotEqualTo(last.id());
        }
    }

    private void session(UUID user, ExerciseDefinition exercise, VariantDefinition variant, Instant createdAt) {
        UUID session = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO training_sessions (id, user_id, type, status, planned_minutes, created_at)
                VALUES (?, ?, 'TRAINING', 'COMPLETED', 10, ?)""", session, user, Timestamp.from(createdAt));
        jdbc.update("""
                INSERT INTO session_items (id, session_id, position, exercise_id, variant_id, category_id,
                                           difficulty_level, seed, params, estimated_seconds)
                VALUES (?, ?, 1, ?, ?, (SELECT id FROM categories WHERE code = ?), 3, 1, '{}'::jsonb, 60)""",
                UUID.randomUUID(), session, exercise.id(), variant.id(), exercise.categoryCode());
    }

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, username, password_hash, created_at) VALUES (?, ?, 'x', now())",
                id, "h" + id.toString().substring(0, 8));
        return id;
    }
}
