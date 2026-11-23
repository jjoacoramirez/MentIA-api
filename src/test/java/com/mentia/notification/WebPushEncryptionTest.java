package com.mentia.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.spec.ECPrivateKeySpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class WebPushEncryptionTest {
    static final String RECEIVER = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    static byte[] decode(String text) { return Base64.getUrlDecoder().decode(text); }

    @Test
    void coincideBytePorByteConElVectorOficialDelRfc8291() throws Exception {
        var privateKey = KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(
                new BigInteger(1, decode("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw")), WebPushEncryption.curve()));
        var publicKey = WebPushEncryption.publicKey(decode("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"));
        byte[] result = WebPushEncryption.encrypt(decode(RECEIVER), decode(AUTH),
                "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.US_ASCII),
                decode("DGv6ra1nlYgDCS1FRnbzlw"), new KeyPair(publicKey, privateKey));
        assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(result)).isEqualTo(
                "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
    }

    @Test
    void rechazaPuntosFueraDeLaCurvaYSecretosInvalidos() {
        byte[] invalid = new byte[65]; invalid[0] = 4;
        assertThatThrownBy(() -> WebPushEncryption.publicKey(invalid)).isInstanceOf(java.security.InvalidKeyException.class);
        assertThatThrownBy(() -> WebPushEncryption.validate(RECEIVER, "AA")).isInstanceOf(java.security.InvalidKeyException.class);
    }

    @Test
    void cadaMensajeUsaUnaClaveYSaltNuevos() throws Exception {
        byte[] content = "{\"kind\":\"start\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(WebPushEncryption.encrypt(RECEIVER, AUTH, content)).isNotEqualTo(WebPushEncryption.encrypt(RECEIVER, AUTH, content));
    }
}
