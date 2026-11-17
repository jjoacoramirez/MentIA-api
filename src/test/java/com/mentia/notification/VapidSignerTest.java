package com.mentia.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

class VapidSignerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneOffset.UTC);

    private static ECPublicKey publicKey() throws Exception {
        byte[] raw = Base64.getUrlDecoder().decode(PushTestKeys.PUBLIC);
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(raw, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(raw, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC")
                .generatePublic(new ECPublicKeySpec(point, params.getParameterSpec(ECParameterSpec.class)));
    }

    @Test
    void signsAJwtTheServiceCanVerifyWithThePublicKey() throws Exception {
        VapidSigner signer = new VapidSigner(PushTestKeys.PUBLIC, PushTestKeys.PRIVATE, PushTestKeys.SUBJECT, CLOCK);

        String header = signer.authorization("https://fcm.googleapis.com/fcm/send/abc123");

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + PushTestKeys.PUBLIC);
        String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
        Claims claims = Jwts.parser().verifyWith(publicKey()).clock(() -> Date.from(CLOCK.instant()))
                .build().parseSignedClaims(jwt).getPayload();
        assertThat(claims.getAudience()).containsExactly("https://fcm.googleapis.com");
        assertThat(claims.getSubject()).isEqualTo(PushTestKeys.SUBJECT);
        // El servicio de push exige un vencimiento de 24 h como máximo.
        assertThat(claims.getExpiration().toInstant()).isBefore(CLOCK.instant().plusSeconds(24 * 3600));
        assertThat(claims.getExpiration().toInstant()).isAfter(CLOCK.instant());
    }

    @Test
    void failsFastOnBadConfiguration() {
        assertThatThrownBy(() -> new VapidSigner("", PushTestKeys.PRIVATE, PushTestKeys.SUBJECT, CLOCK))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MENTIA_PUSH_PUBLIC_KEY");
        assertThatThrownBy(() -> new VapidSigner(PushTestKeys.PUBLIC, "corta", PushTestKeys.SUBJECT, CLOCK))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MENTIA_PUSH_PRIVATE_KEY");
        assertThatThrownBy(() -> new VapidSigner(PushTestKeys.PUBLIC, PushTestKeys.PRIVATE, "mentia", CLOCK))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MENTIA_PUSH_SUBJECT");
    }

    @Test
    void errorMessagesNeverContainTheKeys() {
        assertThatThrownBy(() -> new VapidSigner(PushTestKeys.PUBLIC, "corta", PushTestKeys.SUBJECT, CLOCK))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(PushTestKeys.PRIVATE)
                        .doesNotContain(PushTestKeys.PUBLIC));
    }
}
