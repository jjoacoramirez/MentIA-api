package com.mentia.security;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mentia.security.domain.RevokedToken;

/**
 * Denylist de tokens por {@code jti}. Un token revocado se guarda solo hasta su vencimiento.
 */
@Service
public class TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);

    private final RevokedTokenRepository repository;
    private final Clock clock;

    public TokenRevocationService(RevokedTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void revoke(String jti, Instant expiresAt) {
        repository.save(new RevokedToken(jti, expiresAt));
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(String jti) {
        return repository.existsById(jti);
    }

    @Scheduled(cron = "${mentia.security.revoked-token-cleanup-cron}")
    @Transactional
    public void purgeExpired() {
        int deleted = repository.deleteExpired(Instant.now(clock));
        log.debug("Tokens revocados vencidos eliminados: {}", deleted);
    }
}
