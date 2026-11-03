package com.mentia.gamification;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.gamification.domain.Achievement;
import com.mentia.gamification.domain.UserAchievement;
import com.mentia.gamification.domain.UserProgress;
import com.mentia.gamification.dto.AchievementResponse;
import com.mentia.session.SessionCompletedEvent;
import com.mentia.session.dto.SessionResultResponse.AchievementRef;

/**
 * Logros (BACK-19): se evalúan al completar cada sesión, después de otorgar el XP, y se registran una sola vez.
 * Premian uso y constancia dentro de MentIA.
 */
@Service
public class AchievementService {

    private static final Logger log = LoggerFactory.getLogger(AchievementService.class);

    private final AchievementRepository achievements;
    private final UserAchievementRepository userAchievements;
    private final UserProgressRepository progressRepository;
    private final Clock clock;

    public AchievementService(AchievementRepository achievements, UserAchievementRepository userAchievements,
            UserProgressRepository progressRepository, Clock clock) {
        this.achievements = achievements;
        this.userAchievements = userAchievements;
        this.progressRepository = progressRepository;
        this.clock = clock;
    }

    @EventListener
    @Order(GamificationService.XP_LISTENER_ORDER + 1)
    @Transactional
    public void onSessionCompleted(SessionCompletedEvent event) {
        event.categoriesPracticed().forEach(code -> userAchievements.markCategoryPracticed(event.userId(), code));

        UserProgress progress = progressRepository.findById(event.userId()).orElse(null);
        AchievementRules.Stats stats = new AchievementRules.Stats(
                progress != null ? progress.getSessionsCompleted() : 0,
                progress != null ? progress.getLevel() : 1,
                userAchievements.countCategoriesPracticed(event.userId()));

        Instant now = Instant.now(clock);
        for (Achievement achievement : achievements.findByActiveTrueOrderByPosition()) {
            UserAchievement.Key key = new UserAchievement.Key(event.userId(), achievement.getCode());
            if (!userAchievements.existsById(key) && AchievementRules.isMet(achievement.getCondition(), stats)) {
                userAchievements.save(new UserAchievement(event.userId(), achievement.getCode(), now, event.sessionId()));
            }
        }
    }

    /** Todos los logros activos, con el estado del usuario. */
    @Transactional(readOnly = true)
    public List<AchievementResponse> list(UUID userId) {
        Map<String, Instant> unlocked = new HashMap<>();
        userAchievements.findByIdUserId(userId).forEach(u -> unlocked.put(u.getAchievementCode(), u.getUnlockedAt()));
        return achievements.findByActiveTrueOrderByPosition().stream()
                .map(a -> new AchievementResponse(a.getCode(), a.getName(), a.getDescription(),
                        unlocked.containsKey(a.getCode()), unlocked.get(a.getCode())))
                .toList();
    }

    /** Logros que desbloqueó una sesión, para su resultado. */
    @Transactional(readOnly = true)
    public List<AchievementRef> unlockedIn(UUID sessionId) {
        return userAchievements.findBySessionId(sessionId).stream()
                .map(u -> achievements.findById(u.getAchievementCode())
                        .map(a -> new AchievementRef(a.getCode(), a.getName()))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    /** Un logro con un tipo de condición desconocido nunca se desbloquea: conviene enterarse al arrancar. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void warnAboutUnknownConditions() {
        achievements.findByActiveTrueOrderByPosition().stream()
                .filter(a -> !AchievementRules.isKnown(a.getCondition()))
                .forEach(a -> log.warn("El logro {} tiene un tipo de condición desconocido: {}", a.getCode(),
                        a.getCondition().get("type")));
    }
}
