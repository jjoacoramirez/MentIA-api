package com.mentia.session;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.session.dto.RecentUsage;

/**
 * Historial de uso de ejercicios y variantes por usuario, derivado de los ítems de sus últimas sesiones
 * (cualquier estado: lo que el usuario ya vio cuenta aunque haya abandonado). No hay tabla de historial aparte.
 */
@Service
public class SessionHistoryService {

    private final SessionRepository repository;

    public SessionHistoryService(SessionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public RecentUsage recentUsage(UUID userId, int lookbackSessions) {
        List<UUID> recent = repository.findRecentIds(userId, PageRequest.of(0, lookbackSessions));
        if (recent.isEmpty()) {
            return RecentUsage.none();
        }
        Map<UUID, Integer> sessionsAgo = new HashMap<>();
        for (int i = 0; i < recent.size(); i++) {
            sessionsAgo.put(recent.get(i), i + 1);
        }

        Map<UUID, Integer> exercises = new HashMap<>();
        Map<UUID, Integer> variants = new HashMap<>();
        for (Object[] row : repository.findItemUsage(recent)) {
            int ago = sessionsAgo.get((UUID) row[0]);
            exercises.merge((UUID) row[1], ago, Math::min);
            variants.merge((UUID) row[2], ago, Math::min);
        }
        return new RecentUsage(Map.copyOf(exercises), Map.copyOf(variants));
    }
}
