package com.mentia.gamification;

import com.mentia.gamification.dto.GamificationState;

/**
 * Fórmulas de XP y nivel general (función pura; los valores vienen de configuración):
 * <ul>
 *   <li>XP por sesión = {@code xpPerSession + xpPerItem × ítems + bonus}, con bonus si la precisión alcanza el umbral.</li>
 *   <li>Pasar del nivel {@code n} al {@code n + 1} requiere {@code xpPerLevelFactor × n} XP, así que llegar al nivel
 *       {@code L} requiere en total {@code xpPerLevelFactor × L × (L − 1) / 2}.</li>
 * </ul>
 */
public class XpPolicy {

    private final GamificationProperties properties;

    public XpPolicy(GamificationProperties properties) {
        this.properties = properties;
    }

    public int xpForSession(int itemsCompleted, double accuracy) {
        int bonus = accuracy >= properties.accuracyBonusThreshold() && itemsCompleted > 0
                ? properties.xpAccuracyBonus()
                : 0;
        return properties.xpPerSession() + properties.xpPerItem() * itemsCompleted + bonus;
    }

    public int levelFor(int xp) {
        int level = 1;
        while (xpToReach(level + 1) <= xp) {
            level++;
        }
        return level;
    }

    public GamificationState state(int xp) {
        int level = levelFor(xp);
        return new GamificationState(xp, level, (int) (xp - xpToReach(level)), properties.xpPerLevelFactor() * level);
    }

    /** XP acumulada necesaria para llegar a {@code level}. */
    long xpToReach(int level) {
        return (long) properties.xpPerLevelFactor() * level * (level - 1) / 2;
    }
}
