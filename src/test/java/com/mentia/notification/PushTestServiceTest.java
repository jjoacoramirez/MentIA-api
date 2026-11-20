package com.mentia.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.mentia.auth.AuthService;
import com.mentia.common.error.ApiException;
import com.mentia.common.error.ErrorCode;
import com.mentia.notification.dto.TestPushResponse;

@ExtendWith(MockitoExtension.class)
class PushTestServiceTest {

    @Mock AuthService auth;
    @Mock NotificationService notifications;
    @Mock PushSender sender;

    final UUID user = UUID.randomUUID();

    private PushTestService service(boolean enabled) {
        NotificationProperties properties = new NotificationProperties(enabled, PushTestKeys.PUBLIC,
                PushTestKeys.PRIVATE, PushTestKeys.SUBJECT, "0 0 19 * * *", 1, 5, Duration.ofHours(6),
                Duration.ofSeconds(3), Duration.ofSeconds(10), List.of("fcm.googleapis.com"));
        return new PushTestService(auth, notifications, sender, properties);
    }

    private NotificationService.Device device(String id) {
        return new NotificationService.Device(UUID.randomUUID(), PushTestKeys.ENDPOINT + id);
    }

    @Test
    void sendsToEveryDeviceIgnoringTheStreakRules() {
        given(auth.findUserId("ana")).willReturn(Optional.of(user));
        given(notifications.devicesOf(user)).willReturn(List.of(device("1"), device("2")));
        given(sender.send(any())).willReturn(PushSender.Result.SENT);

        assertThat(service(true).send("ana")).isEqualTo(new TestPushResponse(2, 2, 0, 0));
        // Es una prueba: no cuenta como el recordatorio del día.
        verify(notifications, never()).markReminded(any(), any());
    }

    @Test
    void removesGoneSubscriptionsAndCountsFailures() {
        NotificationService.Device gone = device("gone");
        given(auth.findUserId("ana")).willReturn(Optional.of(user));
        given(notifications.devicesOf(user)).willReturn(List.of(gone, device("down")));
        given(sender.send(gone.endpoint())).willReturn(PushSender.Result.GONE);
        given(sender.send(PushTestKeys.ENDPOINT + "down")).willReturn(PushSender.Result.FAILED);

        assertThat(service(true).send("ana")).isEqualTo(new TestPushResponse(2, 0, 1, 1, Map.of("FAILED", 1)));
        verify(notifications).remove(gone.subscriptionId());
    }

    @Test
    void reportsFailureReasonsWithoutSubscriptionUrls() {
        given(auth.findUserId("ana")).willReturn(Optional.of(user));
        given(notifications.devicesOf(user)).willReturn(List.of(device("1"), device("2"), device("3")));
        given(sender.send(any())).willReturn(PushSender.Result.FORBIDDEN, PushSender.Result.FORBIDDEN,
                PushSender.Result.CONNECTION_FAILED);

        TestPushResponse result = service(true).send("ana");
        assertThat(result.failed()).isEqualTo(3);
        assertThat(result.failures()).containsExactlyInAnyOrderEntriesOf(Map.of("FORBIDDEN", 2, "CONNECTION_FAILED", 1));
        verify(notifications, never()).remove(any());
        verify(notifications, never()).markReminded(any(), any());
    }

    @Test
    void anAccountWithoutRemindersIsNotAnError() {
        given(auth.findUserId("ana")).willReturn(Optional.of(user));
        given(notifications.devicesOf(user)).willReturn(List.of());

        assertThat(service(true).send("ana")).isEqualTo(new TestPushResponse(0, 0, 0, 0));
        verifyNoInteractions(sender);
    }

    @Test
    void unknownAliasIs404() {
        given(auth.findUserId("nadie")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service(true).send("nadie")).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
            assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        });
    }

    @Test
    void disabledServerIs503AndDoesNotEvenLookTheAccountUp() {
        assertThatThrownBy(() -> service(false).send("ana")).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCode()).isEqualTo(ErrorCode.PUSH_DISABLED);
            assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        });
        verifyNoInteractions(auth, sender);
    }
}
