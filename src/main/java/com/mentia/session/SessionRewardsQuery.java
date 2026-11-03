package com.mentia.session;

import java.util.Optional;
import java.util.UUID;

import com.mentia.session.dto.SessionResultResponse.Rewards;

/**
 * Lo que ganó el usuario al completar una sesión (XP, nivel, logros). La implementa gamificación (BACK-18);
 * si no hay implementación, el resultado informa {@code gamification = null}.
 * Se consulta después de publicar {@link SessionCompletedEvent}, cuando los listeners ya lo procesaron.
 */
public interface SessionRewardsQuery {

    Optional<Rewards> rewardsFor(UUID sessionId);
}
