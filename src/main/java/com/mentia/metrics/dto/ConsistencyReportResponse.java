package com.mentia.metrics.dto;

import java.time.Instant;
import java.util.List;

/**
 * Schema {@code ConsistencyReport} del contrato: evolución del desempeño dentro de MentIA según la constancia,
 * solo en agregados por grupo (sin datos de usuarios individuales). No es una medición de capacidades.
 */
public record ConsistencyReportResponse(Instant generatedAt, String timeZone, Totals totals, List<Group> groups) {

    public record Totals(int users, int usersWithStartingPoint, int usersWithTraining, int sessionsCompleted,
            int sessionsAbandoned, Double abandonRate) {
    }

    /**
     * Un grupo de usuarios con la misma constancia (días activos). Los promedios son null si el grupo no tiene datos
     * para calcularlos.
     *
     * @param maxActiveDays    null en el último grupo (sin tope)
     * @param usersWithTrend   usuarios con al menos dos sesiones completadas: los que entran en la evolución de
     *                         precisión
     * @param avgAccuracyDelta promedio de (precisión de la última sesión − precisión de la primera)
     * @param avgLevelDelta    promedio de (nivel actual − nivel de la primera sesión) sobre cada usuario y área
     */
    public record Group(String label, int minActiveDays, Integer maxActiveDays, int users, int sessionsCompleted,
            int sessionsAbandoned, Double avgSessionsPerUser, Double abandonRate, int usersWithTrend,
            Double avgFirstAccuracy, Double avgLastAccuracy, Double avgAccuracyDelta, Double avgLevelDelta,
            List<CategoryEvolution> categories) {
    }

    /** {@code users}: usuarios del grupo que entrenaron esta área. */
    public record CategoryEvolution(String category, int users, double avgLevelDelta) {
    }
}
