package com.mentia.progress;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.common.error.FieldValidationException;
import com.mentia.gamification.GamificationService;
import com.mentia.progress.dto.CategoryProgress;
import com.mentia.progress.dto.ProgressSummary;
import com.mentia.progress.dto.SessionHistoryPage;

/**
 * Historial y progreso (BACK-14). Todo compara al usuario consigo mismo: no hay rankings ni promedios de otros.
 */
@Service
public class ProgressService {

    /** Rango por defecto de la evolución por categoría cuando no se indica {@code from}. */
    static final int DEFAULT_WEEKS = 12;
    static final int DEFAULT_MONTHS = 6;

    private final ProgressQueries queries;
    private final GamificationService gamificationService;
    private final ProgressProperties properties;
    private final Clock clock;

    public ProgressService(ProgressQueries queries, GamificationService gamificationService,
            ProgressProperties properties, Clock clock) {
        this.queries = queries;
        this.gamificationService = gamificationService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SessionHistoryPage sessions(UUID userId, int page, int size) {
        return new SessionHistoryPage(queries.sessions(userId, page, size), page, size, queries.countSessions(userId));
    }

    @Transactional(readOnly = true)
    public ProgressSummary summary(UUID userId) {
        ZoneId zone = properties.timeZone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        List<LocalDate> last30 = queries.activeDays(userId, zone, today.minusDays(29));

        Integer streak = null;
        if (properties.streaksEnabled()) {
            // Una racha no puede ser más larga que los días con actividad: se busca hacia atrás un año como máximo.
            streak = Streaks.current(queries.activeDays(userId, zone, today.minusDays(365)), today);
        }
        ProgressSummary.DailyGoal dailyGoal = properties.dailyGoalSessions() > 0
                ? new ProgressSummary.DailyGoal(properties.dailyGoalSessions(), queries.completedOn(userId, zone, today))
                : null;

        return new ProgressSummary(queries.countByStatus(userId, "COMPLETED"), queries.countByStatus(userId, "ABANDONED"),
                last30.size(), queries.lastCompletedAt(userId), streak, dailyGoal, gamificationService.state(userId));
    }

    /**
     * Evolución por categoría, agrupada por semana o mes. Sin {@code to}, hasta hoy; sin {@code from},
     * las últimas 12 semanas o 6 meses.
     */
    @Transactional(readOnly = true)
    public List<CategoryProgress> categories(UUID userId, String granularity, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(clock.withZone(properties.timeZone()));
        LocalDate start = from != null ? from
                : "month".equals(granularity) ? end.minusMonths(DEFAULT_MONTHS) : end.minusWeeks(DEFAULT_WEEKS);
        if (start.isAfter(end)) {
            throw new FieldValidationException("from", "La fecha de inicio no puede ser posterior a la de fin");
        }

        Map<String, List<CategoryProgress.Point>> byCategory = new LinkedHashMap<>();
        for (ProgressQueries.CategoryPeriod row : queries.categoryPeriods(userId, properties.timeZone(), granularity,
                start, end)) {
            byCategory.computeIfAbsent(row.category(), c -> new ArrayList<>()).add(new CategoryProgress.Point(
                    row.periodStart(), row.accuracy(), row.avgLevel(), row.sessions()));
        }
        List<CategoryProgress> result = new ArrayList<>();
        byCategory.forEach((category, points) -> result.add(new CategoryProgress(category, points)));
        return result;
    }
}
