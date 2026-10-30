package com.mentia.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final String SECRET = "un-secreto-de-prueba-con-mas-de-32-bytes";

    private final JwtService service = jwtAt(NOW, SECRET);

    @Test
    void elTokenEmitidoSeValidaConSusClaims() {
        UUID userId = UUID.randomUUID();

        JwtService.IssuedToken issued = service.issue(userId, 3);

        AuthenticatedUser user = service.parse(issued.token()).orElseThrow();
        assertThat(user.userId()).isEqualTo(userId);
        assertThat(user.tokenVersion()).isEqualTo(3);
        assertThat(user.jti()).isNotBlank();
        assertThat(user.issuedAt()).isEqualTo(NOW);
        assertThat(user.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(issued.expiresInSeconds()).isEqualTo(86_400);
    }

    @Test
    void cadaTokenTieneUnJtiDistinto() {
        UUID userId = UUID.randomUUID();
        String first = service.parse(service.issue(userId, 0).token()).orElseThrow().jti();
        String second = service.parse(service.issue(userId, 0).token()).orElseThrow().jti();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void unTokenVencidoSeRechaza() {
        String token = service.issue(UUID.randomUUID(), 0).token();
        JwtService later = jwtAt(NOW.plus(Duration.ofHours(25)), SECRET);
        assertThat(later.parse(token)).isEmpty();
    }

    @Test
    void unTokenFirmadoConOtroSecretoSeRechaza() {
        String token = jwtAt(NOW, "otro-secreto-de-prueba-con-mas-de-32-bytes").issue(UUID.randomUUID(), 0).token();
        assertThat(service.parse(token)).isEmpty();
    }

    @Test
    void unTokenAlteradoSeRechaza() {
        String token = service.issue(UUID.randomUUID(), 0).token();
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "x." + parts[2];
        assertThat(service.parse(tampered)).isEmpty();
        assertThat(service.parse("no-es-un-jwt")).isEmpty();
        assertThat(service.parse("")).isEmpty();
    }

    @Test
    void unSecretoCortoOSinDefinirFrenaElArranque() {
        assertThatThrownBy(() -> jwtAt(NOW, "corto"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MENTIA_JWT_SECRET");
        // Así llega el valor si la variable de entorno no está definida.
        assertThatThrownBy(() -> jwtAt(NOW, "${MENTIA_JWT_SECRET}"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static JwtService jwtAt(Instant now, String secret) {
        return new JwtService(new SecurityProperties(secret, Duration.ofHours(24)), Clock.fixed(now, ZoneOffset.UTC));
    }
}
