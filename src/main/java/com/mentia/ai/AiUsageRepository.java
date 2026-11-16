package com.mentia.ai;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Acceso a {@code ai_usage} (contabilidad global) y {@code ai_user_quota} (cuota por usuario). */
@Repository
public class AiUsageRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AiUsageRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Registra un intento. Si lo disparó un usuario y llegó al proveedor (no fue bloqueado), también suma a su cuota,
     * en la misma transacción.
     */
    @Transactional
    public void record(LocalDate day, Instant at, AiPurpose purpose, String model, int tokensIn, int tokensOut,
            long costMicroUsd, AiUsageResult result, UUID userId) {
        jdbc.update("""
                INSERT INTO ai_usage (id, used_on, purpose, model, tokens_in, tokens_out, cost_micro_usd, result, created_at)
                VALUES (:id, :day, :purpose, :model, :tokensIn, :tokensOut, :cost, :result, :at)
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("day", day)
                .addValue("purpose", purpose.name())
                .addValue("model", model)
                .addValue("tokensIn", tokensIn)
                .addValue("tokensOut", tokensOut)
                .addValue("cost", costMicroUsd)
                .addValue("result", result.name())
                .addValue("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC)));

        if (userId != null && result != AiUsageResult.BLOCKED) {
            jdbc.update("""
                    INSERT INTO ai_user_quota (user_id, used_on, calls, cost_micro_usd)
                    VALUES (:userId, :day, 1, :cost)
                    ON CONFLICT (user_id, used_on) DO UPDATE
                    SET calls = ai_user_quota.calls + 1,
                        cost_micro_usd = ai_user_quota.cost_micro_usd + EXCLUDED.cost_micro_usd
                    """, new MapSqlParameterSource()
                    .addValue("userId", userId)
                    .addValue("day", day)
                    .addValue("cost", costMicroUsd));
        }
    }

    /** Gasto global entre dos días (ambos inclusive). */
    public long globalCostBetween(LocalDate from, LocalDate to) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(cost_micro_usd), 0) FROM ai_usage WHERE used_on BETWEEN :from AND :to",
                new MapSqlParameterSource().addValue("from", from).addValue("to", to), Long.class);
    }

    public int userCallsOn(UUID userId, LocalDate day) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(calls), 0) FROM ai_user_quota WHERE user_id = :userId AND used_on = :day",
                new MapSqlParameterSource().addValue("userId", userId).addValue("day", day), Integer.class);
    }

    /** Gasto de un usuario entre dos días (ambos inclusive). */
    public long userCostBetween(UUID userId, LocalDate from, LocalDate to) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(cost_micro_usd), 0) FROM ai_user_quota
                WHERE user_id = :userId AND used_on BETWEEN :from AND :to
                """, new MapSqlParameterSource().addValue("userId", userId).addValue("from", from).addValue("to", to),
                Long.class);
    }
}
