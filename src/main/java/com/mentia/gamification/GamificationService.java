package com.mentia.gamification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.gamification.domain.SessionReward;
import com.mentia.gamification.domain.UserProgress;
import com.mentia.gamification.dto.GamificationState;
import com.mentia.session.SessionCompletedEvent;
import com.mentia.session.SessionRewardsQuery;
import com.mentia.session.dto.SessionResultResponse.AchievementRef;
import com.mentia.session.dto.SessionResultResponse.Rewards;

/**
 * XP y nivel general (BACK-18). Se otorga al completar cualquier sesión, una sola vez por sesión.
 */
@Service
public class GamificationService implements SessionRewardsQuery {

    /** Antes que los logros (BACK-19), que se evalúan con el XP y el nivel ya actualizados. */
    public static final int XP_LISTENER_ORDER = 0;

    private final UserProgressRepository progressRepository;
    private final SessionRewardRepository rewardRepository;
    private final AchievementLookup achievements;
    private final XpPolicy xpPolicy;
    private final Clock clock;

    public GamificationService(UserProgressRepository progressRepository, SessionRewardRepository rewardRepository,
            AchievementLookup achievements, XpPolicy xpPolicy, Clock clock) {
        this.progressRepository = progressRepository;
        this.rewardRepository = rewardRepository;
        this.achievements = achievements;
        this.xpPolicy = xpPolicy;
        this.clock = clock;
    }

    /** Corre dentro de la transacción que completa la sesión: si falla, la sesión tampoco se completa. */
    @EventListener
    @Order(XP_LISTENER_ORDER)
    @Transactional
    public void onSessionCompleted(SessionCompletedEvent event) {
        if (rewardRepository.existsById(event.sessionId())) {
            return;
        }
        Instant now = Instant.now(clock);
        UserProgress progress = progressRepository.findById(event.userId())
                .orElseGet(() -> progressRepository.save(new UserProgress(event.userId(), now)));
        int previousLevel = progress.getLevel();
        int xpEarned = xpPolicy.xpForSession(event.itemsCompleted(), event.accuracy());
        int newLevel = xpPolicy.levelFor(progress.getXp() + xpEarned);
        progress.addSession(xpEarned, newLevel, now);
        rewardRepository.save(new SessionReward(event.sessionId(), event.userId(), xpEarned, progress.getXp(),
                newLevel, newLevel > previousLevel, now));
    }

    @Transactional(readOnly = true)
    public GamificationState state(UUID userId) {
        return xpPolicy.state(progressRepository.findById(userId).map(UserProgress::getXp).orElse(0));
    }

    @Transactional(readOnly = true)
    public int sessionsCompleted(UUID userId) {
        return progressRepository.findById(userId).map(UserProgress::getSessionsCompleted).orElse(0);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Rewards> rewardsFor(UUID sessionId) {
        return rewardRepository.findById(sessionId).map(reward -> {
            List<AchievementRef> unlocked = achievements.unlockedIn(sessionId);
            return new Rewards(reward.getXpEarned(), reward.getXpTotal(), reward.getLevel(), reward.isLeveledUp(),
                    unlocked);
        });
    }
}
