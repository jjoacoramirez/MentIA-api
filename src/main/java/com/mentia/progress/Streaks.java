package com.mentia.progress;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * Racha: días seguidos con al menos una sesión completada. Termina hoy, o ayer si hoy todavía no se entrenó
 * (así la racha no se "corta" a la mañana antes de entrenar).
 */
final class Streaks {

    private Streaks() {
    }

    static int current(Collection<LocalDate> activeDays, LocalDate today) {
        Set<LocalDate> days = new TreeSet<>(activeDays);
        LocalDate cursor = days.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
