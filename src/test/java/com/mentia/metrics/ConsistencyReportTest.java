package com.mentia.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.mentia.metrics.MetricsQueries.LevelChange;
import com.mentia.metrics.MetricsQueries.UserActivity;
import com.mentia.metrics.dto.ConsistencyReportResponse;
import com.mentia.metrics.dto.ConsistencyReportResponse.CategoryEvolution;
import com.mentia.metrics.dto.ConsistencyReportResponse.Group;

class ConsistencyReportTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

    private final UUID idle = UUID.randomUUID();
    private final UUID once = UUID.randomUUID();
    private final UUID regularA = UUID.randomUUID();
    private final UUID regularB = UUID.randomUUID();

    private ConsistencyReportResponse report() {
        List<UserActivity> users = List.of(
                new UserActivity(idle, 0, 1, 0, 0, null, null, true),
                new UserActivity(once, 1, 0, 1, 1, 0.5, 0.5, true),
                new UserActivity(regularA, 6, 1, 5, 6, 0.6, 0.9, true),
                new UserActivity(regularB, 4, 0, 4, 4, 0.7, 0.8, true));
        List<LevelChange> levels = List.of(
                new LevelChange(once, "ATENCION", 3, 3),
                new LevelChange(regularA, "ATENCION", 3, 5),
                new LevelChange(regularA, "MEMORIA_VISUAL", 2, 3),
                new LevelChange(regularB, "ATENCION", 4, 5));
        return ConsistencyReport.build(users, levels, List.of(1, 2, 4, 8), ZONE, NOW);
    }

    @Test
    void totales() {
        ConsistencyReportResponse.Totals totals = report().totals();

        assertThat(totals.users()).isEqualTo(4);
        assertThat(totals.usersWithTraining()).isEqualTo(3);
        assertThat(totals.sessionsCompleted()).isEqualTo(11);
        assertThat(totals.sessionsAbandoned()).isEqualTo(2);
        assertThat(totals.abandonRate()).isEqualTo(0.154);
    }

    @Test
    void agrupaPorDiasActivosConUnGrupoParaQuienesNoCompletaronSesiones() {
        List<Group> groups = report().groups();

        assertThat(groups).extracting(Group::label)
                .containsExactly("Sin sesiones completadas", "1 día", "2 a 3 días", "4 a 7 días", "8 días o más");
        assertThat(groups).extracting(Group::users).containsExactly(1, 1, 0, 2, 0);
    }

    @Test
    void resumeLaEvolucionDeCadaGrupo() {
        Group regular = report().groups().get(3);

        assertThat(regular.sessionsCompleted()).isEqualTo(10);
        assertThat(regular.avgSessionsPerUser()).isEqualTo(5.0);
        assertThat(regular.abandonRate()).isEqualTo(0.091);
        assertThat(regular.usersWithTrend()).isEqualTo(2);
        assertThat(regular.avgFirstAccuracy()).isEqualTo(0.65);
        assertThat(regular.avgLastAccuracy()).isEqualTo(0.85);
        assertThat(regular.avgAccuracyDelta()).isEqualTo(0.2);
        // (+2 +1 +1) / 3
        assertThat(regular.avgLevelDelta()).isEqualTo(1.33);
        assertThat(regular.categories()).containsExactly(
                new CategoryEvolution("ATENCION", 2, 1.5),
                new CategoryEvolution("MEMORIA_VISUAL", 1, 1.0));
    }

    @Test
    void conUnaSolaSesionNoHayEvolucionDePrecisionYUnGrupoVacioNoTienePromedios() {
        Group single = report().groups().get(1);
        assertThat(single.usersWithTrend()).isZero();
        assertThat(single.avgAccuracyDelta()).isNull();
        assertThat(single.avgLevelDelta()).isEqualTo(0.0);

        Group empty = report().groups().get(2);
        assertThat(empty.avgSessionsPerUser()).isNull();
        assertThat(empty.abandonRate()).isNull();
        assertThat(empty.avgLevelDelta()).isNull();
        assertThat(empty.categories()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(nullValues = "-", value = {
            "1, 1, 1 día",
            "2, 3, 2 a 3 días",
            "3, 3, 3 días",
            "8, -, 8 días o más",
            "1, -, 1 día o más"})
    void etiquetas(int min, Integer max, String label) {
        assertThat(ConsistencyReport.label(min, max)).isEqualTo(label);
    }
}
