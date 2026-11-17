package com.mentia.security;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.security.domain.RevokedToken;
import com.mentia.security.domain.UserTokenVersion;

/**
 * Revocación de tokens:
 * <ul>
 *   <li>Uno solo (logout): denylist por {@code jti}, guardado hasta su vencimiento.</li>
 *   <li>Todos los de un usuario (reset de contraseña): se incrementa su versión de tokens.</li>
 * </ul>
 */
@Service
public class TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);

    private final RevokedTokenRepository revokedTokens;
    private final UserTokenVersionRepository tokenVersions;
    private final Clock clock;

    public TokenRevocationService(RevokedTokenRepository revokedTokens, UserTokenVersionRepository tokenVersions,
            Clock clock) {
        this.revokedTokens = revokedTokens;
        this.tokenVersions = tokenVersions;
        this.clock = clock;
    }

    @Transactional
    public void revoke(String jti, Instant expiresAt) {
        revokedTokens.save(new RevokedToken(jti, expiresAt));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        tokenVersions.increment(userId);
    }

    @Transactional(readOnly = true)
    public int currentVersion(UUID userId) {
        return tokenVersions.findById(userId).map(UserTokenVersion::getVersion).orElse(0);
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(AuthenticatedUser user) {
        return user.tokenVersion() < currentVersion(user.userId()) || revokedTokens.existsById(user.jti());
    }

    @Scheduled(cron = "${mentia.security.revoked-token-cleanup-cron}")
    @Transactional
    public void purgeExpired() {
        int deleted = revokedTokens.deleteExpired(Instant.now(clock));
        log.debug("Tokens revocados vencidos eliminados: {}", deleted);
    }
}
