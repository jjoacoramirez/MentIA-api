package com.mentia.notification;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** RFC 8291 y 8188: un registro aes128gcm; primitivas del JDK, sin cambiar las claves VAPID. */
final class WebPushEncryption {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RECORD_SIZE = 4096;

    static ECParameterSpec curve() throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    static ECPublicKey publicKey(byte[] encoded) throws GeneralSecurityException {
        if (encoded.length != 65 || encoded[0] != 4) throw new InvalidKeyException("Clave de suscripción inválida");
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(encoded, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(encoded, 33, 65));
        ECParameterSpec spec = curve();
        BigInteger p = ((ECFieldFp) spec.getCurve().getField()).getP();
        BigInteger rhs = x.pow(3).add(spec.getCurve().getA().multiply(x)).add(spec.getCurve().getB()).mod(p);
        if (x.compareTo(p) >= 0 || y.compareTo(p) >= 0 || !y.pow(2).mod(p).equals(rhs))
            throw new InvalidKeyException("Punto fuera de la curva P-256");
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), spec));
    }

    static void validate(String p256dh, String auth) throws GeneralSecurityException {
        publicKey(Base64.getUrlDecoder().decode(p256dh));
        if (Base64.getUrlDecoder().decode(auth).length != 16) throw new InvalidKeyException("Secreto de suscripción inválido");
    }

    static byte[] encrypt(String p256dh, String auth, byte[] plaintext) throws GeneralSecurityException {
        validate(p256dh, auth);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
        KeyPair sender = generator.generateKeyPair();
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(Base64.getUrlDecoder().decode(p256dh), Base64.getUrlDecoder().decode(auth), plaintext, salt, sender);
    }

    /** Variante determinista para verificar el vector de interoperabilidad del RFC 8291, sección 5. */
    static byte[] encrypt(byte[] receiver, byte[] auth, byte[] plaintext, byte[] salt, KeyPair sender) throws GeneralSecurityException {
        if (auth.length != 16 || salt.length != 16 || plaintext.length > 3993) throw new InvalidKeyException("Parámetros de push inválidos");
        ECPublicKey senderKey = (ECPublicKey) sender.getPublic();
        byte[] asPublic = new byte[65];
        asPublic[0] = 4;
        coordinate(senderKey.getW().getAffineX(), asPublic, 1);
        coordinate(senderKey.getW().getAffineY(), asPublic, 33);
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(sender.getPrivate());
        agreement.doPhase(publicKey(receiver), true);
        byte[] prkKey = hmac(auth, agreement.generateSecret());
        byte[] ikm = hmac(prkKey, concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), receiver, asPublic, new byte[]{1}));
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, "Content-Encoding: aes128gcm\0\1".getBytes(StandardCharsets.US_ASCII)), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, "Content-Encoding: nonce\0\1".getBytes(StandardCharsets.US_ASCII)), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] encrypted = cipher.doFinal(concat(plaintext, new byte[]{2}));
        return ByteBuffer.allocate(86 + encrypted.length).put(salt).putInt(RECORD_SIZE).put((byte)65).put(asPublic).put(encrypted).array();
    }

    private static void coordinate(BigInteger value, byte[] target, int offset) {
        byte[] raw = value.toByteArray();
        int count = Math.min(32, raw.length);
        System.arraycopy(raw, raw.length - count, target, offset + 32 - count, count);
    }
    private static byte[] hmac(byte[] key, byte[] input) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(input);
    }
    private static byte[] concat(byte[]... arrays) {
        int size = Arrays.stream(arrays).mapToInt(a -> a.length).sum();
        ByteBuffer result = ByteBuffer.allocate(size);
        for (byte[] array : arrays) result.put(array);
        return result.array();
    }
}
