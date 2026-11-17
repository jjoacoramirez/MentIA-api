package com.mentia.gamification.dto;

/**
 * Schema {@code GamificationState} del contrato.
 *
 * @param xpIntoLevel    XP acumulada dentro del nivel actual
 * @param xpForNextLevel XP que hace falta dentro del nivel actual para pasar al siguiente
 */
public record GamificationState(int xp, int level, int xpIntoLevel, int xpForNextLevel) {
}
