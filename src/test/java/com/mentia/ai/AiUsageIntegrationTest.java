package com.mentia.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mentia.support.IntegrationTest;

class AiUsageIntegrationTest extends IntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 18);

    @Autowired
    AiUsageRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AiClient aiClient;

    @Autowired
    GuardedAiService guardedAiService;

    @Test
    void porDefectoLaIaEstaDeshabilitadaYNoTocaLaBase() {
        assertThat(aiClient).isInstanceOf(DisabledAiClient.class);
        Long before = jdbc.queryForObject("SELECT count(*) FROM ai_usage", Long.class);

        try {
            guardedAiService.generate(AiPurpose.CONTENT_GENERATION, new AiRequest(null, "hola", 10));
        } catch (AiUnavailableException expected) {
            assertThat(expected.reason()).isEqualTo(AiUnavailableException.Reason.DISABLED);
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_usage", Long.class)).isEqualTo(before);
    }

    @Test
    void registraElUsoYSumaPorDiaYPorMes() {
        // Fechas lejanas para no mezclarse con otros tests que comparten la base.
        LocalDate d1 = LocalDate.of(2031, 3, 5);
        LocalDate d2 = LocalDate.of(2031, 3, 20);
        LocalDate other = LocalDate.of(2031, 4, 2);
        repository.record(d1, Instant.parse("2031-03-05T10:00:00Z"), AiPurpose.CONTENT_GENERATION, "claude-haiku-5-5",
                300, 300, 180, AiUsageResult.OK, null);
        repository.record(d2, Instant.parse("2031-03-20T10:00:00Z"), AiPurpose.CONTENT_GENERATION, "claude-haiku-5-5",
                100, 100, 60, AiUsageResult.OK, null);
        repository.record(other, Instant.parse("2031-04-02T10:00:00Z"), AiPurpose.CONTENT_GENERATION, "claude-haiku-5-5",
                100, 100, 60, AiUsageResult.OK, null);

        assertThat(repository.globalCostBetween(d1, d1)).isEqualTo(180);
        assertThat(repository.globalCostBetween(LocalDate.of(2031, 3, 1), LocalDate.of(2031, 3, 31))).isEqualTo(240);
        assertThat(repository.globalCostBetween(LocalDate.of(2031, 1, 1), LocalDate.of(2031, 1, 31))).isZero();
    }

    @Test
    void laCuotaDelUsuarioCuentaLlamadasYCostoPeroNoLasBloqueadas() {
        UUID user = newUser();
        repository.record(DAY, Instant.parse("2026-11-18T10:00:00Z"), AiPurpose.USER_FEEDBACK, "claude-haiku-5-5",
                1000, 300, 250, AiUsageResult.OK, user);
        repository.record(DAY, Instant.parse("2026-11-18T11:00:00Z"), AiPurpose.USER_FEEDBACK, "claude-haiku-5-5",
                0, 0, 0, AiUsageResult.ERROR, user);
        repository.record(DAY, Instant.parse("2026-11-18T12:00:00Z"), AiPurpose.USER_FEEDBACK, "claude-haiku-5-5",
                0, 0, 0, AiUsageResult.BLOCKED, user);

        assertThat(repository.userCallsOn(user, DAY)).isEqualTo(2);
        assertThat(repository.userCostBetween(user, LocalDate.of(2026, 11, 1), DAY)).isEqualTo(250);
        assertThat(repository.userCallsOn(user, DAY.plusDays(1))).isZero();
        assertThat(repository.userCallsOn(UUID.randomUUID(), DAY)).isZero();
    }

    @Test
    void alBorrarElUsuarioSeBorraSuCuotaPeroLaContabilidadGlobalQueda() {
        UUID user = newUser();
        LocalDate day = LocalDate.of(2032, 6, 10);
        repository.record(day, Instant.parse("2032-06-10T10:00:00Z"), AiPurpose.USER_FEEDBACK, "claude-haiku-5-5",
                100, 100, 60, AiUsageResult.OK, user);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_user_quota WHERE user_id = ?", Long.class, user)).isEqualTo(1);

        jdbc.update("DELETE FROM users WHERE id = ?", user);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_user_quota WHERE user_id = ?", Long.class, user)).isZero();
        assertThat(repository.globalCostBetween(day, day)).isEqualTo(60);
    }

    @Test
    void laTablaDeUsoNoGuardaNingunDatoDeUsuarioNiContenido() {
        var columns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'ai_usage'", String.class);
        assertThat(columns).doesNotContain("user_id", "prompt", "response", "content");
    }

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, username, password_hash, created_at) VALUES (?, ?, 'x', now())",
                id, "ai" + id.toString().substring(0, 8));
        return id;
    }
}
