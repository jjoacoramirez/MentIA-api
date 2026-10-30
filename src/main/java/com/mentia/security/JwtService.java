package com.mentia.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Emite y valida JWT HS256 con {@code sub} = userId, {@code jti}, {@code iat} y {@code exp}.
 */
@Service
public class JwtService {

    static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;

    public JwtService(SecurityProperties properties, Clock clock) {
        byte[] secret = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "MENTIA_JWT_SECRET tiene que estar definida y tener al menos " + MIN_SECRET_BYTES + " bytes.");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.ttl = properties.jwtTtl();
        this.clock = clock;
    }

    public IssuedToken issue(UUID userId) {
        Instant now = clock.instant();
        String token = Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, ttl.toSeconds());
    }

    /** Vacío si el token es inválido, está vencido o le falta algún claim. Nunca lanza. */
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (claims.getSubject() == null || claims.getId() == null
                    || claims.getIssuedAt() == null || claims.getExpiration() == null) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(UUID.fromString(claims.getSubject()), claims.getId(),
                    claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record IssuedToken(String token, long expiresInSeconds) {
    }
}
