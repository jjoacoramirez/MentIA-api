package com.mentia.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PushEndpointPolicyTest {

    private final PushEndpointPolicy policy = new PushEndpointPolicy(
            List.of("fcm.googleapis.com", "updates.push.services.mozilla.com", "push.apple.com"));

    @ParameterizedTest
    @ValueSource(strings = {
            "https://fcm.googleapis.com/fcm/send/abc",
            "https://updates.push.services.mozilla.com/wpush/v2/abc",
            "https://web.push.apple.com/QAbc",
            "https://FCM.googleapis.com:443/fcm/send/abc" })
    void acceptsKnownPushServices(String endpoint) {
        assertThat(policy.isAllowed(endpoint)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://fcm.googleapis.com/fcm/send/abc",
            "https://evil.example/fcm.googleapis.com",
            "https://fcm.googleapis.com.evil.example/x",
            "https://evilfcm.googleapis.com/x",
            "https://user:pass@fcm.googleapis.com/x",
            "https://fcm.googleapis.com:8443/x",
            "https://fcm.googleapis.com/x#frag",
            "https://127.0.0.1/x",
            "https://localhost/x",
            "ftp://fcm.googleapis.com/x",
            "no es una url",
            "" })
    void rejectsEverythingElse(String endpoint) {
        assertThat(policy.isAllowed(endpoint)).isFalse();
    }

    @Test
    void rejectsNullAndTooLong() {
        assertThat(policy.isAllowed(null)).isFalse();
        assertThat(policy.isAllowed("https://fcm.googleapis.com/" + "a".repeat(PushEndpointPolicy.MAX_LENGTH)))
                .isFalse();
    }
}
