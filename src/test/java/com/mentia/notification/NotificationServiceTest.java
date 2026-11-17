package com.mentia.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;

class NotificationServiceTest {

    private final PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);

    private NotificationService service(boolean enabled) {
        NotificationProperties properties = new NotificationProperties(enabled, PushTestKeys.PUBLIC,
                PushTestKeys.PRIVATE, PushTestKeys.SUBJECT, "0 0 19 * * *", 1, 5, Duration.ofHours(6),
                Duration.ofSeconds(3), Duration.ofSeconds(10), List.of("fcm.googleapis.com"));
        return new NotificationService(repository, properties,
                new PushEndpointPolicy(properties.allowedHostSuffixes()), Clock.systemUTC());
    }

    @Test
    void disabledServerHidesTheKeyAndRefusesSubscriptions() {
        NotificationService service = service(false);

        assertThat(service.config().enabled()).isFalse();
        assertThat(service.config().publicKey()).isNull();
        assertThatThrownBy(() -> service.subscribe(UUID.randomUUID(), PushTestKeys.ENDPOINT))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.PUSH_DISABLED);
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
        verifyNoInteractions(repository);
    }

    @Test
    void enabledServerExposesOnlyThePublicKey() {
        assertThat(service(true).config().publicKey()).isEqualTo(PushTestKeys.PUBLIC)
                .isNotEqualTo(PushTestKeys.PRIVATE);
    }

    @Test
    void propertiesNeverPrintTheirSecrets() {
        NotificationProperties properties = new NotificationProperties(true, PushTestKeys.PUBLIC,
                PushTestKeys.PRIVATE, PushTestKeys.SUBJECT, "0 0 19 * * *", 1, 5, Duration.ofHours(6),
                Duration.ofSeconds(3), Duration.ofSeconds(10), List.of("fcm.googleapis.com"));
        assertThat(properties.toString()).doesNotContain(PushTestKeys.PRIVATE);
    }
}
