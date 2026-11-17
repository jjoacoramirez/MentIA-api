package com.mentia.session.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Hace cuántas sesiones se usó por última vez cada ejercicio y cada variante (1 = la sesión más reciente).
 * Lo que no aparece no se usó dentro de la ventana consultada.
 */
public record RecentUsage(Map<UUID, Integer> exercisesSessionsAgo, Map<UUID, Integer> variantsSessionsAgo) {

    public static RecentUsage none() {
        return new RecentUsage(Map.of(), Map.of());
    }
}
