package com.mentia.gamification;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mentia.session.dto.SessionResultResponse.AchievementRef;

/**
 * Logros desbloqueados en una sesión, para sumarlos al resultado. Sin logros cargados (BACK-19 los agrega)
 * la lista es vacía.
 */
@Component
public class AchievementLookup {

    public List<AchievementRef> unlockedIn(UUID sessionId) {
        return List.of();
    }
}
