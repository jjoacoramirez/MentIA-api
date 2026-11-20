package com.mentia.notification;

import java.math.BigInteger;
import java.net.URI;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

import io.jsonwebtoken.Jwts;

/**
 * Firma el JWT de VAPID (RFC 8292, ES256) que identifica a esta API ante el servicio de push. Las claves vienen en el
 * formato de {@code web-push generate-vapid-keys}: base64url de la clave pública sin comprimir (65 bytes) y de la
 * privada (32 bytes).
 */
final class VapidSigner {

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();

    private final String publicKey;
    private final ECPrivateKey privateKey;
    private final String subject;
    private final Clock clock;

    VapidSigner(String publicKey, String privateKey, String subject, Clock clock) {
        byte[] pub = decode(publicKey, "MENTIA_PUSH_PUBLIC_KEY");
        if (pub.length != 65 || pub[0] != 0x04) {
            throw new IllegalStateException("MENTIA_PUSH_PUBLIC_KEY no es una clave pública P-256 válida");
        }
        byte[] priv = decode(privateKey, "MENTIA_PUSH_PRIVATE_KEY");
        if (priv.length != 32) {
            throw new IllegalStateException("MENTIA_PUSH_PRIVATE_KEY no es una clave privada P-256 válida");
        }
        if (subject == null || !(subject.startsWith("https://") || subject.startsWith("mailto:"))) {
            throw new IllegalStateException("MENTIA_PUSH_SUBJECT tiene que ser una URL https o un mailto:");
        }
        this.publicKey = publicKey;
        this.subject = subject;
        this.clock = clock;
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec spec = params.getParameterSpec(ECParameterSpec.class);
            this.privateKey = (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, priv), spec));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cargar la clave privada VAPID", e);
        }
    }

    private static byte[] decode(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " es obligatoria con las notificaciones habilitadas");
        }
        try {
            return B64.decode(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + " tiene que estar en base64url");
        }
    }

    /** Valor del header {@code Authorization} para el servicio de push de ese endpoint. */
    String authorization(String endpoint) {
        URI uri = URI.create(endpoint);
        Instant now = Instant.now(clock);
        // El proveedor de Web Push espera aud como un único origen, serializado como texto.
        String jwt = Jwts.builder()
                .audience().single(uri.getScheme() + "://" + uri.getHost())
                .subject(subject)
                .expiration(java.util.Date.from(now.plusSeconds(12 * 3600L)))
                .signWith(privateKey, Jwts.SIG.ES256)
                .compact();
        return "vapid t=" + jwt + ", k=" + publicKey;
    }
}
