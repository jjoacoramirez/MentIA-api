package com.mentia.metrics;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import com.mentia.metrics.MetricsQueries.LevelChange;
import com.mentia.metrics.MetricsQueries.UserActivity;
import com.mentia.metrics.dto.ConsistencyReportResponse;
import com.mentia.metrics.dto.ConsistencyReportResponse.CategoryEvolution;
import com.mentia.metrics.dto.ConsistencyReportResponse.Group;
import com.mentia.metrics.dto.ConsistencyReportResponse.Totals;

/**
 * Agrupa a los usuarios por constancia (días con al menos una sesión completada) y resume cada grupo. Lógica pura:
 * recibe los datos por usuario y devuelve solo agregados.
 */
final class ConsistencyReport {

    private ConsistencyReport() {
    }

    static ConsistencyReportResponse build(List<UserActivity> users, List<LevelChange> levels, List<Integer> floors,
            ZoneId zone, Instant now) {
        List<Group> groups = new ArrayList<>();
        groups.add(group("Sin sesiones completadas", 0, 0, users, levels));
        for (int i = 0; i < floors.size(); i++) {
            int min = floors.get(i);
            Integer max = i + 1 < floors.size() ? floors.get(i + 1) - 1 : null;
            groups.add(group(label(min, max), min, max, users, levels));
        }

        int completed = users.stream().mapToInt(UserActivity::completed).sum();
        int abandoned = users.stream().mapToInt(UserActivity::abandoned).sum();
        Totals totals = new Totals(users.size(),
                (int) users.stream().filter(UserActivity::hasStartingPoint).count(),
                (int) users.stream().filter(u -> u.completed() > 0).count(),
                completed, abandoned, rate(abandoned, completed + abandoned));
        return new ConsistencyReportResponse(now, zone.getId(), totals, groups);
    }

    static String label(int min, Integer max) {
        if (max == null) {
            return min + (min == 1 ? " día o más" : " días o más");
        }
        if (min == max) {
            return min + (min == 1 ? " día" : " días");
        }
        return min + " a " + max + " días";
    }

    private static Group group(String label, int min, Integer max, List<UserActivity> all, List<LevelChange> levels) {
        List<UserActivity> members = all.stream()
                .filter(u -> u.activeDays() >= min && (max == null || u.activeDays() <= max))
                .toList();
        Set<UUID> ids = members.stream().map(UserActivity::userId).collect(Collectors.toSet());
        List<UserActivity> withTrend = members.stream()
                .filter(u -> u.sessionsWithAccuracy() >= 2 && u.firstAccuracy() != null && u.lastAccuracy() != null)
                .toList();
        List<LevelChange> groupLevels = levels.stream().filter(l -> ids.contains(l.userId())).toList();

        Map<String, List<LevelChange>> byCategory = new TreeMap<>(groupLevels.stream()
                .collect(Collectors.groupingBy(LevelChange::category)));
        List<CategoryEvolution> categories = new ArrayList<>();
        byCategory.forEach((category, list) -> categories.add(
                new CategoryEvolution(category, list.size(), round(levelDelta(list), 2))));

        int completed = members.stream().mapToInt(UserActivity::completed).sum();
        int abandoned = members.stream().mapToInt(UserActivity::abandoned).sum();
        return new Group(label, min, max, members.size(), completed, abandoned,
                members.isEmpty() ? null : round((double) completed / members.size(), 2),
                rate(abandoned, completed + abandoned),
                withTrend.size(),
                average(withTrend, UserActivity::firstAccuracy),
                average(withTrend, UserActivity::lastAccuracy),
                average(withTrend, u -> u.lastAccuracy() - u.firstAccuracy()),
                groupLevels.isEmpty() ? null : round(levelDelta(groupLevels), 2),
                categories);
    }

    private static double levelDelta(List<LevelChange> levels) {
        return levels.stream().mapToInt(l -> l.currentLevel() - l.startLevel()).average().orElseThrow();
    }

    private static Double average(List<UserActivity> users, ToDoubleFunction<UserActivity> value) {
        return users.isEmpty() ? null : round(users.stream().mapToDouble(value).average().orElseThrow(), 3);
    }

    private static Double rate(int part, int total) {
        return total == 0 ? null : round((double) part / total, 3);
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
